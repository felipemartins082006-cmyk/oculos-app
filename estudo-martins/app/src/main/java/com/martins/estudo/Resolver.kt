package com.martins.estudo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.messages.BetaBase64ImageSource
import com.anthropic.models.beta.messages.BetaContentBlockParam
import com.anthropic.models.beta.messages.BetaImageBlockParam
import com.anthropic.models.beta.messages.BetaJsonOutputFormat
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.BetaTextBlockParam
import com.anthropic.models.beta.messages.MessageCreateParams
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.Duration

/**
 * Manda as fotos da prova para o Claude e recebe cada questão
 * com a resposta e a resolução passo a passo.
 */
object Resolver {

  private const val MODEL = "claude-opus-5-5"

  private val SYSTEM = """
    Você ajuda um estudante a revisar provas antigas que ele mesmo já fez e reimprimiu para treinar.
    Nas fotos há uma ou mais questões. Para cada questão visível:
    - "numero": o número da questão como aparece na folha (ou a ordem, se não houver número).
    - "enunciado": o enunciado transcrito. Se for muito longo, resuma mantendo todos os dados necessários para resolver.
    - "resposta": a resposta final, curta (no máximo 120 caracteres), porque vai aparecer na tela pequena dos óculos.
    - "resolucao": a resolução passo a passo, didática, em português, explicando o raciocínio e as fórmulas usadas, para o estudante aprender a fazer sozinho.
    Se a questão parecer de múltipla escolha mas as alternativas não aparecem, resolva como questão aberta.
    Se alguma parte estiver ilegível, diga isso na resolução em vez de inventar.
    "titulo": um título curto para a prova (matéria e assunto, se der para perceber).
  """.trimIndent()

  /** Esquema da resposta: o Claude devolve exatamente este JSON. */
  private val schema: Map<String, Any> = mapOf(
      "type" to "object",
      "additionalProperties" to false,
      "required" to listOf("titulo", "questoes"),
      "properties" to mapOf(
          "titulo" to mapOf("type" to "string"),
          "questoes" to mapOf(
              "type" to "array",
              "items" to mapOf(
                  "type" to "object",
                  "additionalProperties" to false,
                  "required" to listOf("numero", "enunciado", "resposta", "resolucao"),
                  "properties" to mapOf(
                      "numero" to mapOf("type" to "string"),
                      "enunciado" to mapOf("type" to "string"),
                      "resposta" to mapOf("type" to "string"),
                      "resolucao" to mapOf("type" to "string"),
                  ),
              ),
          ),
      ),
  )

  /** Roda fora da thread principal (é uma chamada de rede que pode levar 1–2 minutos). */
  fun resolver(context: Context, apiKey: String, fotos: List<Uri>): Prova {
    require(apiKey.isNotBlank()) { "Coloque sua chave da API do Claude em Configurações." }
    require(fotos.isNotEmpty()) { "Escolha pelo menos uma foto." }

    val client = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .timeout(Duration.ofMinutes(5))
        .build()

    val blocks = fotos.map { uri ->
      BetaContentBlockParam.ofImage(
          BetaImageBlockParam.builder()
              .source(BetaBase64ImageSource.builder()
                  .mediaType(BetaBase64ImageSource.MediaType.IMAGE_JPEG)
                  .data(fotoEmBase64(context, uri))
                  .build())
              .build())
    } + BetaContentBlockParam.ofText(
        BetaTextBlockParam.builder()
            .text("Resolva as questões desta prova, seguindo as instruções.")
            .build())

    val params = MessageCreateParams.builder()
        .model(MODEL)
        .maxTokens(16000L)
        .system(SYSTEM)
        // Se o modelo principal recusar, o servidor tenta outro modelo automaticamente.
        .addBeta("server-side-fallback-2026-07-01")
        .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        .outputConfig(BetaOutputConfig.builder()
            .effort(BetaOutputConfig.Effort.HIGH)
            .format(BetaJsonOutputFormat.builder()
                .schema(BetaJsonOutputFormat.Schema.builder()
                    .apply { schema.forEach { (k, v) -> putAdditionalProperty(k, JsonValue.from(v)) } }
                    .build())
                .build())
            .build())
        .addUserMessageOfContentBlockParams(blocks)
        .build()

    val response = try {
      client.beta().messages().create(params)
    } catch (e: UnauthorizedException) {
      throw IllegalStateException("Chave da API inválida. Confira em Configurações.")
    } catch (e: RateLimitException) {
      throw IllegalStateException("Muitos pedidos seguidos. Espere um minuto e tente de novo.")
    } catch (e: AnthropicServiceException) {
      throw IllegalStateException("Erro no Claude (${e.statusCode()}). Tente de novo.")
    }

    val stop = response.stopReason().orElse(null)
    if (stop == BetaStopReason.REFUSAL) {
      throw IllegalStateException("O Claude não quis resolver esta foto.")
    }
    if (stop == BetaStopReason.MAX_TOKENS) {
      throw IllegalStateException("A prova é grande demais para uma vez só. Mande menos fotos por vez.")
    }

    val text = response.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
    if (text.isBlank()) throw IllegalStateException("O Claude não devolveu nenhuma questão.")
    return Prova.fromJson(JSONObject(text), id = System.currentTimeMillis())
  }

  /** Reduz a foto (lado maior até 1568 px, o tamanho que o Claude aproveita) e converte para JPEG. */
  private fun fotoEmBase64(context: Context, uri: Uri): String {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
      decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
      val w = info.size.width
      val h = info.size.height
      val scale = minOf(1f, 1568f / maxOf(w, h))
      if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
    }
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
    bitmap.recycle()
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
  }
}
