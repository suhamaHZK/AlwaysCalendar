# 卓常カレンダー 祝日付き（Always Calendar）

常時表示向けの卓上カレンダーアプリです。予定管理や Google カレンダー連携は行いません。日本の祝日表示に対応予定です。

**対応OS:** Android 10（API 29）以上（compileSdk / targetSdk **36**）  
**applicationId:** `com.kmmm_engineering.alwayscalendar`  
**表示名:** 卓常カレンダー 祝日付き  
**初期バージョン:** 0.0.1（versionCode 1）

## Features（予定・スケルトン時点）

- 没入型の黒背景フルスクリーンカレンダー（当月＋前後月）
- 日曜・祝日は赤、土曜は青、当日ハイライト
- 週始まり（日曜／月曜）を設定で選択
- 待機輝度（デフォルト約 10%）、タップで約 60 秒明るくする
- 有機 EL 焼け防止の表示位置シフト（後続）
- ダブルタップ → 設定（誤操作防止スライダーは後続）
- Discord Webhook／閾値通知（充電時計と同系統・後続）
- [holidays-jp](https://holidays-jp.github.io/) による祝日取得（MIT・後続）
- 広告・課金なし

## Build

```bash
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew :app:assembleDebug
```

- Debug APK: `app/build/outputs/apk/debug/`
- Release AAB: `./gradlew :app:bundleRelease -PappDebug=`（`keystore.properties` が必要。見本は `keystore.properties.example`）

Requires **JDK 17+** and **Android SDK 36**.

バージョン表記:

- サイドロード／debug: `0.0.1 debug001`（`APP_DEBUG`、デフォルト `001`。上書きは `-PappDebug=…`）
- ストア向け（`APP_DEBUG` 空）: `ver.0.0.1`

## Stack

- AGP 8.7.x / Kotlin 2.0.x / Compose BOM 2024.10.01
- DataStore Preferences / Material3 / material-icons-core

## Docs

- 要件: [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md)
- カスタム休日/平日インポート JSON（手書き）: [`docs/CUSTOM_DAYS_IMPORT.md`](docs/CUSTOM_DAYS_IMPORT.md)
- 参考レイアウト画像: [`docs/reference/`](docs/reference/)

## License

本リポジトリのソースは **MIT License** です。詳細は [`LICENSE`](LICENSE) を参照してください。

### サードパーティ（予定）

| ライブラリ / データ | ライセンス | リンク |
|---------------------|------------|--------|
| holidays-jp（祝日 JSON） | MIT | https://holidays-jp.github.io/ / https://github.com/holiday-jp/holiday_jp |

アプリ内のクレジット表記にも同趣旨の記載があります。AndroidX などその他の依存は各成果物のライセンスに従ってください。
