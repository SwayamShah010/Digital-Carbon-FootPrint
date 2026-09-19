# Local Storage Carbon Auditor

(Formerly "Digital Carbon Auditor". Started as a local-files-only tool;
Phase 4 added an optional Google Drive module, so the name is now a bit
narrower than the scope — a full rename is a nice-to-have, not urgent.)

A desktop Java application that scans a folder or drive, analyzes storage
usage, finds duplicate/old/large/temporary files, estimates the associated
digital carbon footprint using cited energy and grid-carbon figures, and
recommends cleanup actions — all through a JavaFX dashboard. Nothing is ever
deleted automatically; the app only flags files for the user's own review.

## Requirements

- JDK 17 or later
- Maven 3.8+
- Internet access on first build (Maven needs to download JavaFX and
  SQLite JDBC dependencies from Maven Central)

## Project Structure

```
src/main/java/com/carbonauditor/
├── Main.java                     Application entry point (JavaFX launcher)
├── model/
│   ├── FileRecord.java           Metadata for a single scanned file
│   └── ScanResult.java           Aggregated results of one scan
├── scanner/
│   └── FileScanner.java          Module 1 — walks the file system (NIO.2)
├── analyzer/
│   ├── FileAnalyzer.java         Module 2 — classifies files by extension
│   ├── DuplicateDetector.java    Module 7 — size compare + SHA-256 hashing
│   └── CleanupAnalyzer.java      Module 8 — flags old/large/temp files, scores them
├── carbon/
│   ├── CarbonCalculator.java     Module 9/11 — carbon footprint & savings estimate
│   └── GridRegion.java           Preset grid carbon-intensity factors by region
├── cloud/
│   ├── GoogleDriveAuth.java      Module 15 — OAuth2 consent flow for Google Drive
│   └── CloudFileScanner.java     Module 15 — lists Drive files as FileRecords
├── recommendation/
│   └── RecommendationEngine.java Module 10 — human-readable suggestions
├── database/
│   └── DatabaseManager.java      Module 14 — SQLite persistence (JDBC)
└── controller/
    ├── ScanController.java       Orchestrates the full scan pipeline
    └── DashboardController.java  Module 12 — JavaFX dashboard controller

src/main/resources/
├── views/Dashboard.fxml          Dashboard layout
└── css/style.css                 Dashboard styling
```

## How to Build & Run

```bash
# Compile
mvn clean compile

# Run the JavaFX dashboard directly
mvn javafx:run

# OR build a runnable fat jar
mvn clean package
java -jar target/digital-carbon-auditor-1.0.0.jar
```

On first run, `carbon_auditor.db` (SQLite) is created automatically in the
working directory, along with the `scans`, `files`, `duplicates`, and
`recommendations` tables.

## How It Works

1. **Scan** — pick a folder; `FileScanner` walks it recursively with
   `Files.walkFileTree`, collecting name, path, size, extension, and
   timestamps. System/OS folders (Windows, Program Files, node_modules,
   .git, etc.) are skipped automatically.
2. **Classify** — each file is bucketed into Documents, Images, Videos,
   Audio, Archives, Applications, Temporary, or Other by extension.
3. **Find duplicates** — files are first grouped by exact size (cheap),
   then SHA-256 hashed only within same-size groups (expensive step
   done as little as possible) to confirm true duplicates.
4. **Flag cleanup candidates** — files not modified in over a year, files
   over 500 MB, and temp/cache/log files are flagged and given a weighted
   cleanup score (duplicates score highest).
5. **Estimate carbon impact** — `CarbonCalculator` applies a configurable
   energy-per-GB and grid-carbon-intensity factor to translate stored GB
   into an estimated kg CO2e/year. These factors are clearly separated
   constants so they can be swapped for cited, up-to-date figures in your
   project report — the app is explicit that these are *approximate*
   estimates, not exact emissions.
6. **Recommend & persist** — `RecommendationEngine` turns the numbers into
   plain-English suggestions; `DatabaseManager` saves the scan, the flagged
   files, and the recommendations to SQLite so scan history can be tracked
   over time.
7. **Track over time** — the dashboard's History tab reads that saved
   history back via `DatabaseManager.getScanHistory()` to show a trend
   chart of storage/carbon across scans, and the Overview tab shows a
   "since your last scan of this folder" comparison banner automatically.

## Configuring the Carbon Model

`CarbonCalculator` exposes two constructor parameters / setters, both with
defaults sourced from published figures rather than invented numbers:

**Energy Usage Factor** — kWh consumed per GB stored per year. Default:
**0.1 kWh/GB/year**, a modern estimate for cloud/data-center storage
reflecting recent efficiency gains (AI Monks, *"The Hidden Cost of the
Cloud"*, 2026, citing recent industry analyses). Older academic estimates
of 3–7 kWh/GB/year exist (Carnegie Mellon University study, cited in
*Stanford Magazine*, *"Carbon and the Cloud"*) but are now considered
outdated given data-center efficiency improvements. Note that this app
scans *local* files — local-disk energy alone is far lower (~0.000005
kWh/GB per the same Stanford article) — the cloud/data-center figure is
used deliberately because most personal files are effectively backed up
or synced to the cloud in practice, so it better approximates a user's
real end-to-end footprint. This assumption is stated explicitly in the
app's UI (see the "How this is estimated" note on the Overview tab) so
it's never presented as an unconditional, precise figure.

**Carbon Intensity Factor** — kg CO2 per kWh of electricity. Default:
**0.445 kg CO2/kWh**, the IEA's 2024 global average power-sector carbon
intensity (IEA, *"Electricity 2025"* report). Grid intensity varies a lot
by country — e.g. India's Central Electricity Authority weighted-average
emission factor for FY2024-25 is 0.710 kg CO2/kWh, while Sweden/France sit
far lower due to nuclear/hydro-heavy grids — so this factor is
intentionally left configurable per region rather than hard-coded.

For your report, feel free to swap in a region-specific figure and cite
it directly; `CarbonCalculator.getMethodologyNote()` returns a short,
plain-English description of whatever factors are currently configured,
which the dashboard displays automatically.

## Google Drive Setup

The cloud storage module (Module 15) is read-only — it can never modify or
delete anything in your Drive — but it does require a one-time OAuth setup
since it authenticates as *your* app, not a shared one:

1. Go to the [Google Cloud Console](https://console.cloud.google.com/),
   create a project (or reuse one), and enable the **Google Drive API**
   under "APIs & Services".
2. Under "APIs & Services -> Credentials", click **Create Credentials ->
   OAuth client ID**, choose **Desktop app**, and create it.
3. Download the resulting JSON file, rename it to `credentials.json`, and
   place it in the project's working directory (the same folder where
   `carbon_auditor.db` gets created — typically the project root when
   running via `mvn javafx:run`, or next to the jar when running the
   packaged app).
4. Click "Scan Google Drive" in the app. The first time, your browser
   will open asking you to sign in and grant read-only access. After
   that, a token is cached in a local `tokens/` folder so you won't be
   asked again.

If `credentials.json` is missing, the app shows an error pointing back to
these steps instead of crashing.

**Scope requested:** `drive.metadata.readonly` — file names, sizes,
timestamps, and checksums only. File *contents* are never downloaded, and
nothing is ever written to or deleted from your Drive by this app.

**Duplicate detection differs for Drive files:** instead of downloading
file bytes to hash them (slow and unnecessary), the app uses Drive's own
`md5Checksum` field, which Google computes and returns as file metadata.
Google-native files (Docs, Sheets, Slides) have no fixed byte content or
checksum, so they're included in storage/category totals but excluded
from duplicate detection.

## Running Tests

Unit tests cover the modules with the clearest pass/fail behavior:
`FileAnalyzer` (classification), `DuplicateDetector` (size+hash matching,
using real temp files), `CleanupAnalyzer` (old/large/temp flagging and
scoring), and `CarbonCalculator` (the carbon math itself).

```bash
mvn test
```

## Notes / Safety

- The app **never deletes files**. It only lists candidates for cleanup;
  actually removing anything is left to the user, outside the app.
- Only files inside the selected root are scanned — no system-wide access.
- Unreadable/locked files are skipped silently rather than crashing the scan.
- Any scan (local or Google Drive) can be stopped mid-way with the
  **Cancel Scan** button. Cancelled scans are never saved to the database
  or shown as results — the dashboard just reverts to whatever the last
  completed scan showed.

## Suggested Next Steps

- Add an in-app "Open file location" / "Reveal in Explorer" action for
  flagged files instead of deleting from within the app.
- Region/country picker to swap the carbon intensity factor per locale.

## Roadmap (out of scope for this version)

- Email/attachment bloat scanning.
- Scheduled background scans and a settings screen for thresholds.
- OneDrive / Dropbox support alongside Google Drive.
