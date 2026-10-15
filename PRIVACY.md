# Privacy Policy — Always Calendar (卓常カレンダー 祝日付き)

Effective date: 2026-10-16
Last updated: 2026-10-16

Package name: `com.kmmm_engineering.alwayscalendar` (developer: KMMM-Engineering)

This document is the privacy policy for the Always Calendar Android app. The app does not ship an in-app privacy screen; this file is intended for the Google Play listing and GitHub Pages / repository viewing. A Japanese version follows the English one.

## Summary

1. **On-device settings only** — Preferences (week start, brightness, colors, landscape mode, exit-on-unplug, auto-sleep, misoperation lock, custom holidays/weekdays, Discord webhook URL, battery alert thresholds, and related options) are stored locally with Android DataStore. They are not uploaded to a developer-operated server. Custom holidays/weekdays can be exported/imported only through files you choose yourself.

2. **Holiday data download** — To show Japanese public holidays, the app sends an HTTPS GET request to `https://holidays-jp.github.io/api/v1/<year>/date.json` (a public static API hosted on GitHub Pages). The request contains only the year in the URL; no account or personal data is sent. As with any web request, the host can see your IP address. The downloaded data is cached in the app's private storage. GitHub's privacy policy applies to data GitHub receives.

3. **Optional Discord webhook** — If you enter a webhook URL, the app may HTTP POST a short text message (for example "Battery 80% (charging)" and the optional test message) to that URL when a battery threshold you configured is crossed, or when you press the test send button. The message contains the battery percentage and charging state only. The app does not run its own notification server. Leave the webhook empty to disable this. Discord's privacy policy applies to data Discord receives.

4. **Battery information** — The app reads the device battery level and charging state locally to display and to evaluate alert thresholds. This is not sent anywhere except in the optional Discord message above.

5. **Saving the webhook icon** — If you tap "Get icon for Webhook", the app saves a bundled PNG image to `DCIM/KMMM-Engineering` through Android MediaStore. No storage permission is required, and the app does not read your other photos or files.

6. **No accounts, ads, or analytics** — No account registration; no advertising SDKs; no analytics or tracking SDKs; no in-app purchases. The app does not collect your location, contacts, or identifiers.

7. **Permissions** — `INTERNET` (holiday download and optional Discord webhook), `ACCESS_NETWORK_STATE` and `ACCESS_WIFI_STATE` (to detect connectivity). Other features work offline. Cleartext (HTTP) traffic is disabled.

8. **Backup** — The app allows Android's standard backup (`allowBackup`), so your on-device settings (including a webhook URL, if set) may be included in your own Google/device backup according to your device settings. The developer cannot access them.

9. **No sale or sharing of data** — The developer does not receive, sell, or share your data. Data leaves the device only as described in items 2 and 3.

10. **Deleting data** — Clear the webhook URL in settings to stop sending. Uninstalling the app or clearing its storage removes the settings and cached holiday data. The saved icon image in your photos can be deleted by you.

## Contact

Use the project's public GitHub repository issues for questions about this policy:
https://github.com/suhamaHZK/AlwaysCalendar/issues

---

# プライバシーポリシー — 卓常カレンダー 祝日付き

施行日: 2026-10-16
最終更新日: 2026-10-16

パッケージ名: `com.kmmm_engineering.alwayscalendar`（開発者: KMMM-Engineering）

## 概要

1. **設定は端末内のみ** — 週の開始曜日、輝度、色、横向き、取り外し時の終了、自動スリープ、誤操作防止、カスタム休日／平日、Discord Webhook URL、バッテリー通知のしきい値などの設定は Android DataStore で端末内に保存され、開発者のサーバーへは送信されません。カスタム休日／平日の書き出し・取り込みは、ユーザーが選んだファイルを介してのみ行われます。

2. **祝日データの取得** — 日本の祝日を表示するため、`https://holidays-jp.github.io/api/v1/<年>/date.json`（GitHub Pages 上の公開 API）へ HTTPS GET を行います。送るのは URL 中の年のみで、アカウント情報や個人情報は送りません。通信の性質上、接続先には IP アドレスが見えます。取得したデータはアプリ専用領域にキャッシュされます。GitHub が受け取る情報には GitHub のプライバシーポリシーが適用されます。

3. **任意の Discord Webhook** — Webhook URL を入力した場合のみ、設定したバッテリーしきい値を跨いだとき、またはテスト送信ボタンを押したときに、その URL へ短いテキスト（例:「バッテリー 80%（充電中）」、テストメッセージ）を HTTP POST します。内容はバッテリー残量と充電状態のみです。独自の通知サーバーはありません。無効にするには Webhook を空にしてください。Discord が受け取る情報には Discord のプライバシーポリシーが適用されます。

4. **バッテリー情報** — 表示としきい値判定のため、端末のバッテリー残量・充電状態を端末内で読み取ります。上記の Discord 通知以外には送信しません。

5. **Webhook 用アイコンの保存** — 「Webhook用のアイコンを入手」を押したときのみ、同梱の PNG 画像を Android の MediaStore 経由で `DCIM/KMMM-Engineering` に保存します。ストレージ権限は不要で、他の写真やファイルは読み取りません。

6. **アカウント・広告・解析なし** — アカウント登録、広告 SDK、解析・トラッキング SDK、アプリ内課金はありません。位置情報、連絡先、広告 ID などは収集しません。

7. **権限** — `INTERNET`（祝日取得と任意の Discord Webhook）、`ACCESS_NETWORK_STATE`／`ACCESS_WIFI_STATE`（接続状態の確認）。その他の機能はオフラインで動作します。HTTP（非暗号化）通信は無効です。

8. **バックアップ** — アプリは Android 標準のバックアップ（`allowBackup`）を許可しているため、端末内の設定（設定済みなら Webhook URL を含む）が、端末の設定に従ってご自身の Google／端末のバックアップに含まれる場合があります。開発者はアクセスできません。

9. **データの販売・共有なし** — 開発者はデータを受け取らず、販売・共有もしません。端末外に出るのは上記 2・3 の場合のみです。

10. **データの削除** — 設定で Webhook URL を空にすれば送信は止まります。アンインストールまたはストレージ消去で、設定と祝日キャッシュは削除されます。フォトに保存したアイコン画像はご自身で削除できます。

## お問い合わせ

本ポリシーに関するご質問は、プロジェクトの公開 GitHub リポジトリの Issues をご利用ください:
https://github.com/suhamaHZK/AlwaysCalendar/issues
