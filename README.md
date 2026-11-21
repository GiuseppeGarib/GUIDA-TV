# GUIDA TV ITALIA

Applicazione Android nativa per consultare i palinsesti dei canali televisivi italiani in chiaro.

## Obiettivo
Interfaccia compatta e immediata, ispirata alla praticità delle guide TV classiche (in particolare all'organizzazione della versione 3.8 di SUPER GUIDA TV), ma con codice, grafica e implementazione originali.

## Stato del progetto
Sviluppo avviato. La prima fase imposta l'app Android e la struttura tecnica. Prima di distribuire una sorgente EPG preconfigurata occorre verificarne espressamente licenza e autorizzazione alla redistribuzione: la disponibilità pubblica di un file XMLTV non equivale a una licenza di riutilizzo.

## Funzioni previste
- Elenco canali e palinsesto per fascia oraria
- Programma in onda e successivo
- Navigazione per giorno
- Ricerca per titolo e descrizione
- Preferiti e promemoria locali
- Aggiornamento periodico e cache offline
- Impostazioni di tema, aggiornamento e sorgente EPG
- Importazione XMLTV da una sorgente scelta dall'utente

## Requisiti
- Android Studio recente
- JDK 17
- Android SDK 35

## Build
Aprire il progetto in Android Studio e attendere la sincronizzazione Gradle. In alternativa, con Gradle installato:
```bash
gradle :app:assembleDebug
```

## Dati EPG e diritti
L'app non include palinsesti di terzi nel repository. La sorgente XMLTV deve essere scelta e utilizzata nel rispetto delle condizioni del relativo fornitore e dei diritti sui dati. Il parser è progettato per il formato XMLTV standard.
