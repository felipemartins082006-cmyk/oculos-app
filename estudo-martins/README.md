# EstudoMartins

App Android para revisar provas antigas com o Meta Ray-Ban Display.

1. Escolha até 5 fotos da galeria (o seletor de fotos do Android, sem permissões extras).
2. O app envia as fotos para o Claude (modelo `claude-opus-5-5`, saída em JSON estruturado),
   que devolve cada questão com a resposta e a resolução passo a passo.
3. As provas ficam salvas só no celular.
4. **Revisar nos óculos**: flashcards na lente (pergunta → Ver resposta → Acertei/Errei)
   usando o Meta Wearables Device Access Toolkit (módulo Display).

A chave da API do Claude é digitada no próprio app e fica salva só no celular.

## Compilar

    ./gradlew assembleDebug
