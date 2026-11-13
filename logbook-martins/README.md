# Logbook (LogbookMartins)

App Android para registrar o treino da academia, no lugar do canal do Discord.
Tudo fica salvo só no celular: sem conta, sem internet e sem permissões.

## Como usar

**Treino**: o dia de hoje já vem aberto. Digite uma série por vez, do mesmo jeito do Discord:

    squat 160kg x 4
    bench press -> 94kg x 4 reps (1 RIR)
    squat 160 x 4, 4, 3          (três séries de uma vez)
    lower-back extension 20kg x 3 sets x 8 reps
    pull ups bw x 10             (só o peso do corpo)

- Enquanto você digita o nome, aparecem os seus exercícios como sugestão, e embaixo
  aparece o que você fez da última vez e o seu recorde.
- Quando uma série bate o recorde de carga ou de 1RM estimado, aparece um aviso 🏆.
- Toque numa série para editar ou apagar. Use "+ repetir" para repetir a última série.
- Use ‹ › ou toque na data para registrar ou corrigir outro dia.

**Histórico**: todos os dias de treino, do mais novo para o mais antigo, com filtro por exercício.

**Evolução**: um gráfico por exercício (carga, reps, 1RM estimado e volume), com período
de 1 mês a tudo, recordes e todos os treinos. Também dá para renomear um exercício
ou juntar dois nomes (por exemplo, um erro de digitação).

**Dados**: importar mensagens do Discord (colando o texto ou escolhendo um .txt),
salvar backup e apagar tudo. Na importação, uma série que já está no app não entra
repetida, então pode importar um texto que se sobrepõe ao que já tem.

O app já vem com o logbook de 07/06/25 a 23/07/25 (`app/src/main/assets/logbook_inicial.txt`).

## Instalar

Baixe `apk/LogbookMartins.apk` no celular e abra. O Android pede para permitir
"instalar apps desconhecidos". As versões novas instalam por cima, sem perder os registros.

## Compilar

    ./gradlew testDebugUnitTest assembleRelease

O APK sai em `app/build/outputs/apk/release/app-release.apk`. Os testes também tiram
fotos de cada tela em `app/build/telas/`.
