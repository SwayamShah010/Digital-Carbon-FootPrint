package com.carbonauditor.controller;

import com.carbonauditor.carbon.GridRegion;
import com.carbonauditor.database.DatabaseManager;
import com.carbonauditor.model.FileRecord;
import com.carbonauditor.model.Recommendation;
import com.carbonauditor.model.ScanResult;
import com.carbonauditor.recommendation.RecommendationEngine;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Module 12: Dashboard controller. Wires the JavaFX view (Dashboard.fxml)
 * to the ScanController pipeline: summary metric cards, a storage-by-category
 * pie chart, a filterable table of files flagged for cleanup (with the
 * reason each was flagged), and a card-based, priority-sorted recommendations
 * view.
 */
public class DashboardController {

    @FXML private Label pathLabel;
    @FXML private Label totalStorageLabel;
    @FXML private Label potentialCleanupLabel;
    @FXML private Label duplicateStorageLabel;
    @FXML private Label largeFilesLabel;
    @FXML private Label carbonImpactLabel;
    @FXML private Label carbonSavingsLabel;
    @FXML private Label carbonEquivalentLabel;
    @FXML private Label carbonEquivalentPhoneLabel;
    @FXML private Label methodologyLabel;
    @FXML private ComboBox<GridRegion> regionPicker;
    @FXML private ProgressBar scanProgressBar;
    @FXML private Button scanButton;
    @FXML private Button cloudScanButton;
    @FXML private Button cancelButton;
    @FXML private PieChart categoryPieChart;

    @FXML private TextField fileFilterField;
    @FXML private TableView<FileRecord> recommendationsTable;
    @FXML private TableColumn<FileRecord, String> nameColumn;
    @FXML private TableColumn<FileRecord, String> sourceColumn;
    @FXML private TableColumn<FileRecord, String> categoryColumn;
    @FXML private TableColumn<FileRecord, String> reasonColumn;
    @FXML private TableColumn<FileRecord, Number> sizeColumn;
    @FXML private TableColumn<FileRecord, Number> scoreColumn;

    @FXML private VBox recommendationsBox;

    @FXML private HBox comparisonBanner;
    @FXML private Label comparisonLabel;

    @FXML private LineChart<String, Number> historyChart;
    @FXML private TableView<DatabaseManager.ScanSummary> historyTable;
    @FXML private TableColumn<DatabaseManager.ScanSummary, String> historyDateColumn;
    @FXML private TableColumn<DatabaseManager.ScanSummary, String> historyPathColumn;
    @FXML private TableColumn<DatabaseManager.ScanSummary, Number> historyStorageColumn;
    @FXML private TableColumn<DatabaseManager.ScanSummary, Number> historyCarbonColumn;
    @FXML private TableColumn<DatabaseManager.ScanSummary, Number> historySavingsColumn;

    private final DatabaseManager databaseManager = new DatabaseManager("carbon_auditor.db");
    private final ScanController scanController = new ScanController(databaseManager);
    private final RecommendationEngine recommendationEngine = new RecommendationEngine();

    private ObservableList<FileRecord> flaggedFiles = FXCollections.observableArrayList();
    private ScanResult lastScanResult;
    private Task<ScanResult> currentTask;

    @FXML
    public void initialize() {
        databaseManager.initSchema();

        nameColumn.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        sourceColumn.setCellValueFactory(new PropertyValueFactory<>("source"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        reasonColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(flagReasons(cell.getValue())));
        sizeColumn.setCellValueFactory(cell ->
                new SimpleDoubleProperty(cell.getValue().getFileSizeMB()));
        scoreColumn.setCellValueFactory(new PropertyValueFactory<>("cleanupScore"));

        FilteredList<FileRecord> filtered = new FilteredList<>(flaggedFiles, f -> true);
        fileFilterField.textProperty().addListener((obs, oldVal, newVal) -> {
            String query = newVal == null ? "" : newVal.toLowerCase().trim();
            filtered.setPredicate(f -> query.isEmpty() || f.getFileName().toLowerCase().contains(query));
        });
        recommendationsTable.setItems(filtered);

        regionPicker.setItems(FXCollections.observableArrayList(GridRegion.values()));
        regionPicker.setValue(GridRegion.GLOBAL_AVERAGE);
        regionPicker.valueProperty().addListener((obs, oldRegion, newRegion) -> {
            if (newRegion == null) return;
            scanController.getCarbonCalculator().setCarbonIntensityFactorKgPerKwh(newRegion.getKgCo2PerKwh());
            if (lastScanResult != null) {
                recalculateCarbonAndRefresh(lastScanResult);
            }
        });

        historyDateColumn.setCellValueFactory(new PropertyValueFactory<>("scanDate"));
        historyPathColumn.setCellValueFactory(new PropertyValueFactory<>("scannedPath"));
        historyStorageColumn.setCellValueFactory(cell ->
                new SimpleDoubleProperty(cell.getValue().totalStorage() / (1024.0 * 1024.0 * 1024.0)));
        historyCarbonColumn.setCellValueFactory(cell ->
                new SimpleDoubleProperty(cell.getValue().estimatedCarbonKg()));
        historySavingsColumn.setCellValueFactory(cell ->
                new SimpleDoubleProperty(cell.getValue().potentialSavingsKg()));

        refreshHistory();
    }

    @FXML
    public void onHistoryTabSelected() {
        refreshHistory();
    }

    /** Reloads the scan-history table and trend chart from the database. */
    private void refreshHistory() {
        List<DatabaseManager.ScanSummary> history = databaseManager.getScanHistory();
        historyTable.setItems(FXCollections.observableArrayList(history));

        // Chart wants chronological (oldest -> newest) order, DB returns newest-first
        List<DatabaseManager.ScanSummary> chronological = new ArrayList<>(history);
        java.util.Collections.reverse(chronological);

        XYChart.Series<String, Number> storageSeries = new XYChart.Series<>();
        storageSeries.setName("Total Storage (GB)");
        XYChart.Series<String, Number> carbonSeries = new XYChart.Series<>();
        carbonSeries.setName("Est. Carbon (kg CO2e/yr)");

        DateTimeFormatter shortFmt = DateTimeFormatter.ofPattern("MMM d HH:mm");
        for (DatabaseManager.ScanSummary s : chronological) {
            String label;
            try {
                label = java.time.LocalDateTime.parse(s.scanDate()).format(shortFmt);
            } catch (Exception e) {
                label = s.scanDate();
            }
            storageSeries.getData().add(new XYChart.Data<>(label, s.totalStorage() / (1024.0 * 1024.0 * 1024.0)));
            carbonSeries.getData().add(new XYChart.Data<>(label, s.estimatedCarbonKg()));
        }

        historyChart.getData().setAll(storageSeries, carbonSeries);
    }

    private String flagReasons(FileRecord f) {
        List<String> reasons = new ArrayList<>();
        if (f.isDuplicate()) reasons.add("Duplicate");
        if (f.isOld()) reasons.add("Not used in 1yr+");
        if (f.isLarge()) reasons.add("Large (>500MB)");
        if (f.isTemporary()) reasons.add("Temp/cache");
        return String.join(", ", reasons);
    }

    @FXML
    public void onSelectFolderAndScan() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select a folder to scan");
        Stage stage = (Stage) scanButton.getScene().getWindow();
        File selected = chooser.showDialog(stage);
        if (selected == null) {
            return;
        }
        runScan(selected.getAbsolutePath());
    }

    private void runScan(String path) {
        scanButton.setDisable(true);
        cloudScanButton.setDisable(true);
        showCancelButton(true);
        scanProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        pathLabel.setText("Scanning: " + path);

        Task<ScanResult> task = new Task<>() {
            @Override
            protected ScanResult call() throws Exception {
                return scanController.runFullScan(path,
                        count -> Platform.runLater(() -> pathLabel.setText("Scanning: " + path + "  (" + count + " files)")),
                        this::isCancelled);
            }
        };

        currentTask = task;
        runScanTask(task);
    }

    @FXML
    public void onScanGoogleDrive() {
        scanButton.setDisable(true);
        cloudScanButton.setDisable(true);
        showCancelButton(true);
        scanProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        pathLabel.setText("Connecting to Google Drive...");

        Task<ScanResult> task = new Task<>() {
            @Override
            protected ScanResult call() throws Exception {
                return scanController.runCloudScan(
                        count -> Platform.runLater(() -> pathLabel.setText("Scanning Google Drive...  (" + count + " files)")),
                        this::isCancelled);
            }
        };

        task.setOnFailed(e -> {
            resetScanButtons();
            Throwable ex = task.getException();
            String message = ex != null ? ex.getMessage() : "unknown error";
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Google Drive scan failed: " + message +
                            "\n\nSee the \"Google Drive Setup\" section in README.md for one-time setup steps.");
            alert.showAndWait();
        });

        currentTask = task;
        runScanTaskSuccessOnly(task);
    }

    @FXML
    public void onCancelScan() {
        if (currentTask != null && currentTask.isRunning()) {
            pathLabel.setText("Cancelling scan...");
            cancelButton.setDisable(true);
            currentTask.cancel();
        }
    }

    private void showCancelButton(boolean show) {
        cancelButton.setVisible(show);
        cancelButton.setManaged(show);
        cancelButton.setDisable(!show);
    }

    private void resetScanButtons() {
        scanButton.setDisable(false);
        cloudScanButton.setDisable(false);
        showCancelButton(false);
        scanProgressBar.setProgress(0);
    }

    private void runScanTask(Task<ScanResult> task) {
        task.setOnFailed(e -> {
            resetScanButtons();
            Throwable ex = task.getException();
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Scan failed: " + (ex != null ? ex.getMessage() : "unknown error"));
            alert.showAndWait();
        });
        runScanTaskSuccessOnly(task);
    }

    private void runScanTaskSuccessOnly(Task<ScanResult> task) {
        task.setOnSucceeded(e -> {
            resetScanButtons();
            scanProgressBar.setProgress(1.0);
            displayResult(task.getValue());
        });

        task.setOnCancelled(e -> {
            resetScanButtons();
            pathLabel.setText(lastScanResult != null
                    ? "Scan cancelled. Showing results from the last completed scan."
                    : "Scan cancelled. No results to show yet — select a folder or Drive to try again.");
        });

        Thread thread = new Thread(task, "scan-thread");
        thread.setDaemon(true);
        thread.start();
    }

    private void displayResult(ScanResult result) {
        List<DatabaseManager.ScanSummary> historyBeforeThisScan = databaseManager.getScanHistory();

        lastScanResult = result;

        pathLabel.setText("Last scan: " + result.getScannedPath() +
                "   •   " + result.getAllFiles().size() + " files   •   " +
                String.format("%.1f GB", result.getTotalStorageGB()));

        totalStorageLabel.setText(String.format("%.1f GB", result.getTotalStorageGB()));
        potentialCleanupLabel.setText(String.format("%.1f GB", result.getPotentialCleanupGB()));
        duplicateStorageLabel.setText(String.format("%.1f GB", result.getDuplicateStorageBytes() / (1024.0 * 1024.0 * 1024.0)));
        largeFilesLabel.setText(result.getAllFiles().stream().filter(FileRecord::isLarge).count() + " files");

        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        result.getStorageByCategory().forEach((category, bytes) -> {
            double gb = bytes / (1024.0 * 1024.0 * 1024.0);
            if (gb > 0) {
                pieData.add(new PieChart.Data(category + String.format(" (%.1f GB)", gb), gb));
            }
        });
        categoryPieChart.setData(pieData);

        flaggedFiles.setAll(result.getRecommendedForCleanup());

        updateComparisonBanner(result, historyBeforeThisScan);
        refreshHistory();

        recalculateCarbonAndRefresh(result);
    }

    /**
     * Shows a "since your last scan" banner comparing this scan's total
     * storage to the previous scan of the same folder, if one exists.
     */
    private void updateComparisonBanner(ScanResult result, List<DatabaseManager.ScanSummary> historySnapshot) {
        // historySnapshot was fetched after ScanController already persisted the
        // current scan, so the *first* matching-path entry is this scan itself —
        // skip it and take the second match as the true previous scan.
        DatabaseManager.ScanSummary previous = null;
        boolean skippedCurrent = false;
        for (DatabaseManager.ScanSummary s : historySnapshot) {
            if (result.getScannedPath().equals(s.scannedPath())) {
                if (!skippedCurrent) {
                    skippedCurrent = true;
                    continue;
                }
                previous = s;
                break;
            }
        }

        if (previous == null) {
            comparisonBanner.setVisible(false);
            comparisonBanner.setManaged(false);
            return;
        }

        double previousGb = previous.totalStorage() / (1024.0 * 1024.0 * 1024.0);
        double currentGb = result.getTotalStorageGB();
        double deltaGb = previousGb - currentGb;

        if (Math.abs(deltaGb) < 0.05) {
            comparisonBanner.setVisible(false);
            comparisonBanner.setManaged(false);
            return;
        }

        double deltaCarbonKg = scanController.getCarbonCalculator().estimateCarbonKg(Math.abs(deltaGb));

        String text = deltaGb > 0
                ? String.format("Since your last scan of this folder, you freed up %.1f GB — an estimated %.1f kg CO2e/year saved.",
                        deltaGb, deltaCarbonKg)
                : String.format("Since your last scan of this folder, storage grew by %.1f GB — an estimated %.1f kg CO2e/year more.",
                        -deltaGb, deltaCarbonKg);

        comparisonLabel.setText(text);
        comparisonBanner.setVisible(true);
        comparisonBanner.setManaged(true);
    }

    /**
     * Recomputes carbon impact/savings using the currently selected grid
     * region and refreshes every carbon-dependent label and the
     * recommendation cards, without re-scanning the file system.
     */
    private void recalculateCarbonAndRefresh(ScanResult result) {
        double totalGb = result.getTotalStorageGB();
        double cleanupGb = result.getPotentialCleanupGB();

        result.setEstimatedCarbonKg(scanController.getCarbonCalculator().estimateCarbonKg(totalGb));
        result.setPotentialCarbonSavingsKg(scanController.getCarbonCalculator().estimatePotentialSavingsKg(cleanupGb));

        carbonImpactLabel.setText(String.format("%.1f kg CO2e/yr", result.getEstimatedCarbonKg()));
        carbonSavingsLabel.setText(String.format("%.1f kg CO2e/year  (%.0f%% storage reduction)",
                result.getPotentialCarbonSavingsKg(), result.getPotentialStorageReductionPercent()));

        double equivalentKm = scanController.getCarbonCalculator().kgToEquivalentKmDriven(result.getPotentialCarbonSavingsKg());
        double equivalentCharges = scanController.getCarbonCalculator().kgToEquivalentPhoneCharges(result.getPotentialCarbonSavingsKg());
        carbonEquivalentLabel.setText(String.format("≈ equivalent to %.0f km of car travel avoided per year", equivalentKm));
        carbonEquivalentPhoneLabel.setText(String.format("≈ equivalent to %.0f smartphone charges", equivalentCharges));

        GridRegion region = regionPicker.getValue();
        String regionNote = region != null ? "  Grid region: " + region.getDisplayName() + " (" + region.getSource() + ")." : "";
        methodologyLabel.setText("How this is estimated: " + scanController.getCarbonCalculator().getMethodologyNote() + regionNote);

        renderRecommendationCards(recommendationEngine.generateRecommendations(result));
    }

    private void renderRecommendationCards(List<Recommendation> recommendations) {
        recommendationsBox.getChildren().clear();
        for (Recommendation r : recommendations) {
            recommendationsBox.getChildren().add(buildCard(r));
        }
    }

    private VBox buildCard(Recommendation r) {
        VBox card = new VBox(6);
        card.getStyleClass().add("rec-card");
        card.getStyleClass().add("rec-card-" + r.getPriority().name().toLowerCase());

        HBox titleRow = new HBox(8);
        Label badge = new Label(badgeText(r.getPriority()));
        badge.getStyleClass().addAll("rec-badge", "rec-badge-" + r.getPriority().name().toLowerCase());
        Label title = new Label(r.getTitle());
        title.getStyleClass().add("rec-title");
        titleRow.getChildren().addAll(badge, title);

        Label detail = new Label(r.getDetail());
        detail.setWrapText(true);
        detail.getStyleClass().add("rec-detail");

        Label action = new Label("→ " + r.getAction());
        action.setWrapText(true);
        action.getStyleClass().add("rec-action");

        card.getChildren().addAll(titleRow, detail, action);

        if (r.getPotentialSavingsGb() > 0.01) {
            Label savings = new Label(String.format("Potential savings: %.1f GB", r.getPotentialSavingsGb()));
            savings.getStyleClass().add("rec-savings");
            card.getChildren().add(savings);
        }

        return card;
    }

    private String badgeText(Recommendation.Priority p) {
        return switch (p) {
            case HIGH -> "HIGH PRIORITY";
            case MEDIUM -> "MEDIUM PRIORITY";
            case LOW -> "LOW PRIORITY";
            case INFO -> "INFO";
        };
    }
}
