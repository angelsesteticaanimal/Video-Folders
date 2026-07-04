# Video Folders

Este projeto reproduz vídeos em tela cheia a partir de pastas selecionadas e inclui um modo tipo "bobinas" (reels). Possui crossfade visual e crossfade de áudio suave entre vídeos.

Como testar localmente

1. Instale o Flutter na sua máquina: https://flutter.dev/docs/get-started/install
2. Clone este repositório e entre na pasta:
   git clone https://github.com/angelsesteticaanimal/Video-Folders.git
   cd Video-Folders
3. Gere os arquivos de plataforma (se ainda não existirem):
   flutter create --org com.angelsesteticaanimal .
4. Baixe dependências e rode no dispositivo/emulador:
   flutter pub get
   flutter run

Build (APK via Actions)

O repositório inclui um workflow GitHub Actions que gera um APK debug e o disponibiliza como artifact. Abra o run do workflow e baixe o arquivo app-debug.apk quando o job terminar.
