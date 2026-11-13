# EspelhoMartins

App Android que mostra a tela do celular no Meta Ray-Ban Display, usando o
Meta Wearables Device Access Toolkit (módulo Display).

## Como funciona
1. Captura a tela do celular (MediaProjection) em um serviço em primeiro plano.
2. Recorta conforme o modo escolhido (tela inteira, metade de cima ou de baixo).
3. Reduz para 600×600 e envia como imagem para a tela dos óculos.
   Quadros iguais ao anterior não são reenviados.

## Compilar
Abra esta pasta (`espelho-martins`) no Android Studio e aperte **Run**, ou:

    ./gradlew assembleDebug

O APK fica em `app/build/outputs/apk/debug/app-debug.apk`.

A chave `app/espelho-debug.keystore` é só para instalar versões novas por cima
das antigas no seu celular. Não use para publicar na Play Store.
