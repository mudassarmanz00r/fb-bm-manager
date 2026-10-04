# FB BM Page Manager — Android App (v2.0.0)

A native Android app (Kotlin + Jetpack Compose + Material3) that connects to
**multiple Facebook Business Managers**, lists **all Pages** (owned + client)
per BM, shows each page's **status / stats / earnings**, and **publishes
Reels** to one page, many pages at once, or a different video per page — with
background uploads via WorkManager.

Graph API version: **v26.0** (latest stable, verified Oct 2026).

---

## 1. Setup — Meta App (do this first)

1. Go to **https://developers.facebook.com/apps** → *Create App* → choose the
   **Business** app type (needed for `business_management`).
2. **Add the Android platform**: *App Settings → Basic → Add Platform →
   Android*.
   - **Package Name:** `com.teamc.fbbmmanager`
   - **Key Hash:** for the debug build, run:
     ```
     keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore | openssl sha1 -binary | openssl base64
     ```
     (password: `android`). Add the release key hash too when you sign the app.
   - Save, and copy the **Client Token** (*App Settings → Client Token*) into
     `app/src/main/res/values/strings.xml` (`facebook_client_token`).
3. **App ID is already configured** — this copy ships with
   `local.properties` containing your App ID (`1141717861748207`), wired
   through `BuildConfig.META_APP_ID` into the manifest. One-line change if
   you ever need a different one: edit `META_APP_ID` in `local.properties`
   (or export it as an env var). A blank `local.properties.template` is also
   included for reference.
4. **Test users**: *App Roles → Test Users* → create test users and give them a
   role on your Business Manager + Pages. Development-mode permissions work for
   app-role users without review.
5. **App Review (for real users)**: the permissions below need **Advanced
   Access** via *App Review* (+ Business Verification) before anyone outside
   your app roles can log in:
   `pages_show_list`, `pages_read_engagement`, `pages_manage_posts`,
   `business_management`, `read_insights`, `pages_manage_metadata`.
6. The person logging in must have the **CREATE_CONTENT** task on each Page to
   publish Reels (Business Settings → Pages → people/tasks).

## 2. Build the APK

Prerequisites: **Android Studio** (Koala or newer) with **JDK 17**, or a
machine/VM with **JDK 17 + ~4 GB free RAM** for the Gradle daemon
(a memory-starved Linux VM cannot run the Gradle daemon — the build was
developed and QA'd here, but the final APK must be compiled on a machine
with enough memory).

```bash
cd fb-bm-manager
./gradlew assembleDebug        # APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # needs a signing config (see below)
```

`./gradlew` is a small bootstrap script: on first run it downloads Gradle 8.7
once into `~/.gradle/wrapper/dists` and reuses it afterwards (needs
curl/wget + unzip). On Windows use `gradlew.bat`.

**Release signing:** add your keystore to `app/build.gradle.kts`
(`signingConfigs`) — the debug APK is fine for testing.

## 3. How to use the app

1. **Login** — *Continue with Facebook* (permissions listed above).
2. **Business Managers** — your BM **Paulo's Studio
   (`2114106279493502`) is pre-filled on first launch** — no typing needed.
   Add more numeric BM IDs any time (Business Settings → Business Info);
   they are saved locally, tap one to select it, delete with confirmation.
   The last selected BM is remembered and its pages open directly after
   login. Removing a BM is permanent for that entry — the pre-filled one is
   only seeded once and never re-added after you delete it.
3. **Pages** — owned + client pages of the **selected BM** with profile
   picture, follower count and an Owned/Client badge. Tapping a page fetches
   its Page access token, then opens detail. Switch BMs any time from the
   **BM switcher** in the top bar (appears when 2+ BMs are saved) or the
   overflow menu → *Manage Business Managers*.
4. **Page detail** — three tabs:
   - *Status*: uploaded-video count, Page Integrity status
     (ok/warning/restricted/suspended), violations, branded-content eligibility.
   - *Stats*: 28-day views, reach, engagements, follows, video views.
   - *Earnings*: see §5 (API limitation).
5. **Publish Reels** (upload icon in the pages toolbar):
   - Choose **One video → all pages** or **Per-page videos** (map a different
     video to each page).
   - Type the caption/description, tick pages (All/None shortcuts), press
     **Publish**. Uploads run in **WorkManager** (foreground service) so they
     continue with the app backgrounded; each page shows live progress,
     retries up to 3× automatically, and has an **individual Retry button**
     on failure.
   - Videos over 500 MB show a warning; files over 2 GB are blocked before
     upload (Reels are max 90 s anyway).
   - Meta limit: **30 API-published reels per rolling 24 hours**.
6. **Theme** — premium dark theme by default; toggle dark/light from the
   overflow menu on the Pages screen. Phone + tablet layouts (adaptive grid
   on wide screens), portrait + landscape.

### Session handling

- If Facebook reports an expired/invalid token (OAuth error 190), the app
  logs you out and routes to Login automatically — no stale error screens.
- Page access tokens are fetched per page on demand and kept in memory only
  (per-BM session cache).

## 4. Login permissions requested

| Permission | Used for |
|---|---|
| `pages_show_list` | List pages, get Page access tokens |
| `pages_read_engagement` | Page fields, video lists (dependency of insights/publishing) |
| `pages_manage_posts` | Publish Reels to Pages |
| `business_management` | Read BM `owned_pages` / `client_pages` |
| `read_insights` | Page Insights metrics |
| `pages_manage_metadata` | Page Integrity API (`page_status`) |

## 5. API availability — what the Graph API can and cannot provide

Verified against official Meta docs (Oct 2026, Graph API **v26.0**):

| Feature | API status |
|---|---|
| List BM pages (owned + client) | ✅ `GET /{business-id}/owned_pages`, `GET /{business-id}/client_pages` |
| Page access token | ✅ `GET /{page-id}?fields=access_token` (or `/me/accounts`) |
| Publish Reels (chunked/resumable) | ✅ `POST /{page-id}/video_reels` (start → rupload transfer → finish, `video_state=PUBLISHED`) |
| Uploaded-video count | ✅ `GET /{page-id}/videos?summary=true&limit=0` → `summary.total_count` |
| Violations / restrictions | ✅ Page Integrity API `GET /{page-id}/page_status` (needs `pages_manage_metadata`) |
| Branded-content eligibility | ✅ Page field `is_eligible_for_branded_content` |
| Insights (views/reach/engagement) | ✅ `GET /{page-id}/insights` — current metric names: `page_media_view`, `page_total_media_view_unique`, `page_post_engagements`, `page_daily_follows`, `page_follows` (old `page_impressions*` names are deprecated and now error) |
| **Content Monetization eligibility** | ❌ **No public API.** Check manually in Meta Business Suite → Monetization. The app says this explicitly instead of guessing. |
| **Earnings / payouts** | ❌ **No public API.** Check Meta Business Suite → Monetization → Payouts. The app shows this notice. |

## 6. Verified endpoint reference (docs URLs)

Every Graph API call in the app traces to one of these official pages
(read during development, Oct 2026):

- Graph API changelog / latest version (v26.0):
  https://developers.facebook.com/docs/graph-api/changelog/
- BM pages (owned_pages / client_pages):
  https://developers.facebook.com/docs/business-management-apis/business-asset-management/guides/pages/
- Page reference (incl. `access_token`, `is_eligible_for_branded_content` fields):
  https://developers.facebook.com/docs/graph-api/reference/page/
- Manage a Page (page tokens via `/me/accounts`):
  https://developers.facebook.com/documentation/pages-api/manage-pages
- **Reels publishing** (start / transfer / finish protocol):
  https://developers.facebook.com/documentation/video-api/guides/reels-publishing
- Page Integrity API (`page_status`):
  https://developers.facebook.com/documentation/pages-api/integrity-webhook
- Pages API changelog (insights metric deprecations):
  https://developers.facebook.com/documentation/pages-api/changelog
- Permission access levels (Standard vs Advanced / App Review):
  https://developers.facebook.com/docs/graph-api/overview/access-levels/

## 7. Project structure

```
fb-bm-manager/
├── app/
│   ├── build.gradle.kts            # minSdk 26, targetSdk 34, META_APP_ID wiring
│   └── src/main/
│       ├── AndroidManifest.xml     # FacebookActivity, APP_ID meta-data
│       ├── java/com/teamc/fbbmmanager/
│       │   ├── FbBmApp.kt                  # Application + service locator
│       │   ├── data/
│       │   │   ├── api/GraphApiService.kt  # Retrofit endpoints (verified)
│       │   │   ├── api/GraphClient.kt      # Retrofit/OkHttp builder
│       │   │   ├── api/ReelsUploader.kt    # resumable Reels upload protocol
│       │   │   ├── api/dto/                # Gson DTOs
│       │   │   ├── auth/FacebookAuthManager.kt
│       │   │   ├── local/SessionStore.kt   # multi-BM IDs + selected BM + theme
│       │   │   ├── model/                  # FbPage, MonetizationStatus, …
│       │   │   ├── repository/             # PageRepository + Graph impl + AuthExpiredException
│       │   │   └── work/UploadWorker.kt    # WorkManager bulk uploads
│       │   └── ui/
│       │       ├── MainActivity.kt / nav/  # single activity + NavHost (transitions)
│       │       ├── *ViewModel.kt           # session (multi-BM), pages, detail, upload
│       │       ├── components/             # shimmer skeletons, empty/error states
│       │       ├── screens/                # Login, BM manager, Pages, Detail, Upload
│       │       └── theme/                  # VIP dark-first Material3 theme
│       └── res/                            # strings (externalized), colors, theme, adaptive icon
├── gradle/libs.versions.toml       # version catalog
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── local.properties.template       # → copy to local.properties, add App ID
├── gradlew / gradlew.bat           # Gradle bootstrap scripts
└── README.md
```

## 8. Notes & limits

- Reels spec: MP4, 9:16 (1080×1920 recommended), 3–90 s, H.264/H.265, AAC 48 kHz.
- Chunked upload uses 4 MB chunks with `offset`/`file_size` headers and
  `Authorization: OAuth <page_token>` on the `rupload.facebook.com` host.
- A Page token is fetched per page on demand and kept in memory only.
- Debug builds log basic HTTP traffic (BASIC level) — no tokens in release logs.
