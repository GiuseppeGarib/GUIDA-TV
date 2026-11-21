# GUIDA TV ITALIA

Applicazione Android nativa per consultare i palinsesti dei canali televisivi italiani in chiaro, con interfaccia compatta ispirata all'organizzazione delle guide TV classiche.

L'app è un'implementazione originale: non utilizza codice o grafica proprietari di SUPER GUIDA TV.

## Funzioni disponibili

- **Guida**: elenco canali ordinato per LCN quando riconosciuto, programma in onda, successivo e barra di avanzamento.
- **Timeline**: palinsesto orizzontale per tutti i canali, con fascia dalle 06:00 alle 06:00 del giorno successivo, scorrimento temporale sincronizzato e indicatore dell'ora corrente.
- **Ora in onda**: elenco dei programmi effettivamente in corso.
- **Serata**: programmazione della fascia serale selezionata, con scelta dell'ora di inizio.
- **Ricerca**: ricerca di titoli e descrizioni, con canale, giorno e orario.
- **Preferiti**: salvataggio di canali e singoli programmi.
- **Promemoria**: notifica locale cinque minuti prima dell'inizio del programma.
- **Archivio offline**: cache locale SQLite per consultare l'ultimo palinsesto scaricato.
- **Impostazioni**: tema chiaro/scuro/di sistema, sorgente XMLTV HTTPS e intervallo di aggiornamento automatico configurabile.

## Sorgente e canali

È configurato un feed XMLTV gratuito di terze parti come sorgente iniziale. Il feed può contenere canali con differenti condizioni di accesso: l'app applica una selezione editoriale dei canali italiani in chiaro e salva soltanto i programmi associati ai canali selezionati.

La copertura dipende dalla disponibilità e dalla qualità dei dati pubblicati dal fornitore. La disponibilità pubblica di un file XMLTV non costituisce, da sola, una licenza di redistribuzione: l'utilizzo dei dati deve rispettare le condizioni del fornitore e i diritti applicabili. L'app non inventa programmi o descrizioni mancanti.

## Requisiti

- Android 8.0 (API 26) o successivo
- JDK 17
- Android SDK 35

## Build

Con Gradle installato:

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```

La workflow GitHub Actions esegue la compilazione Debug, i test unitari e pubblica l'APK come artifact della build.

## Struttura tecnica

- Kotlin e Jetpack Compose
- Parser XMLTV
- Cache SQLite
- WorkManager per aggiornamento periodico e promemoria
- JUnit per i test automatici del filtro canali
