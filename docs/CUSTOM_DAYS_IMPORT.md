# カスタム休日／平日インポート用 JSON（手書きガイド）

卓常カレンダーの設定画面から「インポート」で貼り付ける JSON の書き方です。  
実装の根拠: `CustomDayTransfer` / `CustomDayRule`（formatVersion **1**）。

手書き・Keep・メモ帳からの貼付を想定しています。エクスポート結果を編集しても構いません。

---

## 1. 包み形式（推奨）

オブジェクトで休日と平日を一度に渡せます。

| キー | 必須 | 内容 |
|------|------|------|
| `format` | 任意 | `"alwayscalendar-custom-days"`。空または省略可。別文字列は拒否 |
| `formatVersion` | 任意 | 現状 `1`（未使用でも可） |
| `holidays` | ※ | カスタム休日（赤）の規則配列 |
| `weekdays` | ※ | カスタム平日（白・最優先）の規則配列 |

※ `holidays` と `weekdays` の**少なくとも一方**が必要。片方だけの包みも可。

```json
{
  "format": "alwayscalendar-custom-days",
  "formatVersion": 1,
  "holidays": [ /* 規則オブジェクト… */ ],
  "weekdays": [ /* 規則オブジェクト… */ ]
}
```

キーがあるカテゴリだけ適用されます。例えば `holidays` のみの包みを「全置換」しても、既存のカスタム平日は消えません。

---

## 2. 配列のみ形式

規則の配列だけでも受理されます。

```json
[
  { "kind": "ONCE", "year": 2026, "month": 12, "day": 29 }
]
```

この場合、アプリのインポート画面で **適用先カテゴリ（休日／平日）** を必ず選びます。  
包み形式ではカテゴリ選択は不要です（キーで振り分け済み）。

---

## 3. 規則オブジェクト共通

| フィールド | 必須 | 説明 |
|------------|------|------|
| `kind` | **必須** | 下記のいずれか（大文字・完全一致） |
| `id` | 省略可 | 省略時はインポート時に新規 UUID。手書きなら省略でよい |
| `year` / `month` / `day` | kind による | 使わない数値は **0** か **キー省略** |
| `nth` | kind による | 第 N 曜日の N（1〜5） |
| `dayOfWeek` | kind による | **ISO**: 月=1 … 日=7（日=0 ではない） |
| `endYear` / `endMonth` / `endDay` | DATE_RANGE のみ | 期間の終了日（inclusive） |

未知のフィールドは無視されます。`kind` 不正・必須値が範囲外の件はスキップされ、件数だけ報告されます。

### dayOfWeek（ISO）

| 値 | 曜日 |
|----|------|
| 1 | 月 |
| 2 | 火 |
| 3 | 水 |
| 4 | 木 |
| 5 | 金 |
| 6 | 土 |
| 7 | 日 |

---

## 4. 各 kind の必須フィールド

| kind | 意味（UI ラベル） | 必須フィールド |
|------|-------------------|----------------|
| `ONCE` | 1日のみ | `year`, `month`, `day`（実在する年月日） |
| `DATE_RANGE` | 期間指定 | 開始 `year`/`month`/`day` と終了 `endYear`/`endMonth`/`endDay`（どちらも実在日・inclusive）。終了＜開始ならアプリ側で入替 |
| `YEARLY_MD` | 毎年 M 月 D 日 | `month`（1〜12）, `day`（1〜31・その月に存在する日） |
| `YEARLY_NTH_DOW` | 毎年 M 月第 N W 曜日 | `month`（1〜12）, `nth`（1〜5）, `dayOfWeek`（1〜7） |
| `YEARLY_MONTH_END` | 毎年 M 月末 | `month`（1〜12） |
| `MONTHLY_D` | 毎月 D 日 | `day`（1〜31）。その月に無い日（例: 31）は当月はマッチしない |
| `MONTHLY_NTH_DOW` | 毎月第 N W 曜日 | `nth`（1〜5）, `dayOfWeek`（1〜7） |
| `MONTHLY_END` | 毎月末 | 追加フィールドなし（`kind` だけで可） |
| `WEEKLY_DOW` | 毎週 W 曜日 | `dayOfWeek`（1〜7） |

「第 N 曜日」は単一指定のみ（範囲なし）。

---

## 5. 追加／全置換

インポート時にアプリで必ず選びます。

| モード | 動作 |
|--------|------|
| **追加** | 既存リストの末尾に追記 |
| **全置換** | そのカテゴリのリストをインポート内容で置き換え |

- 包み形式: 存在するキー（`holidays` / `weekdays`）のカテゴリにだけ、選んだモードを適用。キーが無いカテゴリは触らない。
- 配列のみ: UI で選んだ 1 カテゴリにだけ適用。
- 全置換でも、選んでいない／キーの無い側のカテゴリは消えません。

---

## 6. 見本（手書きしやすい最小例）

### 6.1 包み・最小（休日に 1 日＋毎週火曜を平日に）

```json
{
  "format": "alwayscalendar-custom-days",
  "formatVersion": 1,
  "holidays": [
    { "kind": "ONCE", "year": 2026, "month": 8, "day": 11 }
  ],
  "weekdays": [
    { "kind": "WEEKLY_DOW", "dayOfWeek": 2 }
  ]
}
```

### 6.2 期間指定（年末年始を休日に）

```json
{
  "format": "alwayscalendar-custom-days",
  "formatVersion": 1,
  "holidays": [
    {
      "kind": "DATE_RANGE",
      "year": 2026,
      "month": 12,
      "day": 29,
      "endYear": 2027,
      "endMonth": 1,
      "endDay": 3
    }
  ]
}
```

### 6.3 配列のみ（アプリで休日か平日かを指定）

```json
[
  { "kind": "YEARLY_MD", "month": 8, "day": 11 },
  { "kind": "YEARLY_NTH_DOW", "month": 10, "nth": 2, "dayOfWeek": 1 },
  { "kind": "MONTHLY_END" }
]
```

---

## 7. よくある誤り

| 誤り | 正しい扱い |
|------|------------|
| 日曜を `dayOfWeek: 0` や `7` 以外 | ISO では **日=7**（月=1） |
| `kind` の小文字・別名（`once`, `Once`, `DATE` など） | 列挙名と**完全一致**（例: `ONCE`, `DATE_RANGE`） |
| 包みなのに `holidays`/`weekdays` が無い | 拒否（「見つかりません」） |
| `holidays` をオブジェクトにする | **配列**であること |
| 未使用フィールドに適当な日付を入れる | 0 または省略でよい（必須でないキーを無理に書かない） |
| `DATE_RANGE` で終了フィールドを忘れる | `endYear`/`endMonth`/`endDay` が無いとスキップ |
| `format` に別アプリ名などを入れる | 拒否。省略するか `alwayscalendar-custom-days` |
| 全置換で「全部消える」と思い込む | **キーがある／選んだカテゴリだけ**置換 |
| 配列のみでカテゴリを JSON に書きたい | 配列にはカテゴリ無し。UI で指定するか包み形式を使う |

---

## 関連

- 要件: [`REQUIREMENTS.md`](REQUIREMENTS.md) の「4d. カスタム休日/平日のエクスポート・インポート」
- ソース: `app/.../data/CustomDayTransfer.kt`, `CustomDayRule.kt`
