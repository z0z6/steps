# Dot Steps

Licznik kroków na Androida (Kotlin + Jetpack Compose), styl matrycy kropek à la Nothing X + tryb E-ink.

## Build na GitHub Actions
1. Wrzuć zawartość tego folderu do repozytorium (gałąź `main`).
2. Actions → "Build APK" uruchomi się samo (lub ręcznie: *Run workflow*).
3. Gotowy plik: Artifacts → **DotSteps-apk** → `app-release.apk` (podpisany kluczem debug, do sideloadu).

## Lokalnie
`gradle assembleRelease` (Gradle 8.9, JDK 17, Android SDK 35).

## Motywy
E-INK, E-INK NOC, NOTHING, SIGNAL, AMBER (Opcje → Motyw).

## Statystyki i dane
Zakładka STATY: 7 dni / 30 dni / rok, suma, średnia, rekord, seria dni z celem, km, kcal, sumy od początku.
Opcje → Dane: eksport i import historii do CSV (`date,steps,km,kcal`).
