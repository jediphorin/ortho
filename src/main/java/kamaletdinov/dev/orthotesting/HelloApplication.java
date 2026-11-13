package kamaletdinov.dev.orthotesting;

import java.io.File;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public class HelloApplication extends Application {

    // =================================================================
    // МЕДИЦИНСКИЕ И ТЕХНИЧЕСКИЕ КОНСТАНТЫ АНАЛИЗА ИНДЕКСА 30:15
    // =================================================================
    // Окно для поиска minRR15 (в районе 15-го удара)
    private static final int MIN_RR15_WINDOW_START = 5;
    private static final int MIN_RR15_WINDOW_END = 25;

    // Окно для поиска maxRR30 (в районе 30-го удара)
    private static final int MAX_RR30_WINDOW_START = 20;
    private static final int MAX_RR30_WINDOW_END = 40;

    // Запас ударов после финиша окна (для стабильности подмассива subList)
    private static final int POST_WINDOW_BUFFER = 5;

    // Автоматический расчет общего размера окна и минимального остатка файла
    private static final int REQUIRED_POST_CHANGE_BEATS = MAX_RR30_WINDOW_END + POST_WINDOW_BUFFER; // 45
    // =================================================================

    private Label fileStatusLabel;

    // Поля ввода времени
    private TextField minLyingStart, secLyingStart;
    private TextField minChange, secChange;
    private TextField minStandingStart, secStandingStart;

    // Текстовые метки результатов на экране
    private Label startRrResultLabel;
    private Label minRr15ResultLabel;   // Добавлено: minRR15
    private Label maxRr30ResultLabel;   // Добавлено: maxRR30
    private Label orthoResultLabel;
    private Label ratioResultLabel;

    private List<Double> rrIntervals = new ArrayList<>();
    private long totalFileDurationMs = 0;
    private static final String CONFIG_FILE = determineConfigPath();

    private static String determineConfigPath() {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");
        String folderName = "OrthoTesting";
        File configFolder;

        if (os.contains("mac")) {
            // Стандарт macOS: ~/Library/Application Support/OrthoTesting/
            configFolder = new File(userHome + "/Library/Application Support/" + folderName);
        } else if (os.contains("win")) {
            // Стандарт Windows: ~\AppData\Roaming\OrthoTesting\
            String appData = System.getenv("APPDATA");
            configFolder = new File((appData != null ? appData : userHome) + File.separator + folderName);
        } else {
            // Стандарт Linux/Unix: ~/.config/OrthoTesting/
            configFolder = new File(userHome + "/.config/" + folderName);
        }

        // КРИТИЧНО ДЛЯ ХОРОШЕЙ ПРАКТИКИ:
        // Если папки еще нет (первый запуск), Java обязана её создать, иначе будет ошибка записи!
        if (!configFolder.exists()) {
            configFolder.mkdirs();
        }

        return configFolder.getAbsolutePath() + File.separator + "ortho_config.properties";
    }

    private void saveTimeSettings() {
        // Вставьте эту строчку первой, чтобы увидеть точный путь в консоли IDEA при нажатии кнопки:
        System.out.println("Пытаюсь сохранить файл настроек по адресу: " + CONFIG_FILE);

        Properties props = new Properties();
        props.setProperty("lyingMin", minLyingStart.getText().trim());
        props.setProperty("lyingSec", secLyingStart.getText().trim());
        props.setProperty("changeMin", minChange.getText().trim());
        props.setProperty("changeSec", secChange.getText().trim());
        props.setProperty("standingMin", minStandingStart.getText().trim());
        props.setProperty("standingSec", secStandingStart.getText().trim());

        try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
            props.store(out, "Файл настроек ортостатической пробы");
        } catch (Exception e) {
            // Если не удалось сохранить, программа не должна падать
            System.err.println("Не удалось сохранить настройки: " + e.getMessage());
        }
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("RR Орто-Анализатор");

        // --- 1. Секция выбора файла ---
        Label headerLabel = new Label("Анализ ортостатической пробы");
        headerLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        Button openButton = new Button("Выбрать CSV-файл");
        fileStatusLabel = new Label("Файл не выбран");
        fileStatusLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #7f8c8d;");

        openButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
            File selectedFile = fileChooser.showOpenDialog(primaryStage);
            if (selectedFile != null) {
                loadCsvData(selectedFile);
            }
        });

        // --- ЗАГРУЗКА ВЧЕРАШНИХ НАСТРОЕК ИЗ ФАЙЛА ---
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
            props.load(in);
        } catch (Exception e) {
            // Файла нет или он поврежден — запишем дефолтные значения в свойства
            props.setProperty("lyingMin", "0");
            props.setProperty("lyingSec", "30");
            props.setProperty("changeMin", "3");
            props.setProperty("changeSec", "0");
            props.setProperty("standingMin", "4");
            props.setProperty("standingSec", "40");
        }

        // --- 2. Секция настройки временных диапазонов ---
        GridPane timeGrid = new GridPane();
        timeGrid.setHgap(10);
        timeGrid.setVgap(10);
        timeGrid.setAlignment(Pos.CENTER);

        // Стабилизация ЛЁЖА (по умолчанию 0:30)
        minLyingStart = new TextField(props.getProperty("lyingMin"));
        minLyingStart.setPrefWidth(40);
        secLyingStart = new TextField(props.getProperty("lyingSec"));
        secLyingStart.setPrefWidth(40);
        timeGrid.add(new Label("Стабилизация ЧСС лёжа (начало):"), 0, 0);
        timeGrid.add(minLyingStart, 1, 0); timeGrid.add(new Label("м."), 2, 0);
        timeGrid.add(secLyingStart, 3, 0); timeGrid.add(new Label("с."), 4, 0);

        // Момент ПОДЪЕМА (по умолчанию 3:00)
        minChange = new TextField(props.getProperty("changeMin"));
        minChange.setPrefWidth(40);
        secChange = new TextField(props.getProperty("changeSec"));
        secChange.setPrefWidth(40);
        timeGrid.add(new Label("Время изменения положения (подъём):"), 0, 1);
        timeGrid.add(minChange, 1, 1); timeGrid.add(new Label("м."), 2, 1);
        timeGrid.add(secChange, 3, 1); timeGrid.add(new Label("с."), 4, 1);

        // Стабилизация СТОЯ (по умолчанию 4:40)
        minStandingStart = new TextField(props.getProperty("standingMin"));
        minStandingStart.setPrefWidth(40);
        secStandingStart = new TextField(props.getProperty("standingSec"));
        secStandingStart.setPrefWidth(40);
        timeGrid.add(new Label("Стабилизация ЧСС стоя (начало):"), 0, 2);
        timeGrid.add(minStandingStart, 1, 2); timeGrid.add(new Label("м."), 2, 2);
        timeGrid.add(secStandingStart, 3, 2); timeGrid.add(new Label("с."), 4, 2);

        // --- 3. Кнопка расчета ---
        Button calculateButton = new Button("Рассчитать показатели");
        calculateButton.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-base: #3498db; -fx-text-fill: white;");
        calculateButton.setOnAction(e -> processAnalysis());

        // --- 4. Секция результатов ---
        startRrResultLabel = new Label("RR в момент начала подъема: --");
        startRrResultLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #34495e;");

        minRr15ResultLabel = new Label("minRR15 (в районе 15-го удара): --");
        minRr15ResultLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #2980b9;");

        maxRr30ResultLabel = new Label("maxRR30 (в районе 30-го удара): --");
        maxRr30ResultLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #27ae60;");

        orthoResultLabel = new Label("Ортостатическая разница: --");
        orthoResultLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        ratioResultLabel = new Label("Коэффициент 30:15: --");
        ratioResultLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #d35400;");

        // --- Компоновка интерфейса ---
        VBox mainLayout = new VBox(12); // Немного уплотнили для вместимости новых строк
        mainLayout.setPadding(new Insets(20));
        mainLayout.setAlignment(Pos.CENTER);
        mainLayout.getChildren().addAll(
                headerLabel, openButton, fileStatusLabel,
                new Separator(), timeGrid, calculateButton,
                new Separator(), startRrResultLabel, minRr15ResultLabel, maxRr30ResultLabel,
                new Separator(), orthoResultLabel, ratioResultLabel
        );

        Scene scene = new Scene(mainLayout, 540, 540);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    private void loadCsvData(File file) {
        rrIntervals.clear();
        totalFileDurationMs = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean isHeader = true;
            int durationIdx = -1;

            while ((line = br.readLine()) != null) {
                String[] columns = line.split(",");
                if (columns.length == 0) continue;

                if (isHeader) {
                    for (int i = 0; i < columns.length; i++) {
                        if (columns[i].trim().equalsIgnoreCase("duration")) {
                            durationIdx = i;
                            break;
                        }
                    }
                    isHeader = false;
                    continue;
                }
                if (durationIdx != -1 && durationIdx < columns.length) {
                    try {
                        double val = Double.parseDouble(columns[durationIdx].trim());
                        rrIntervals.add(val);
                        totalFileDurationMs += (long) val;
                    } catch (NumberFormatException e) {
                        // Пропуск пустых строк
                    }
                }
            }
            if (!rrIntervals.isEmpty()) {
                long totalSec = Math.round((double) totalFileDurationMs / 1000.0);
                fileStatusLabel.setText(String.format("Успешно загружено %d RR (Длина записи: %d мин %02d сек)",
                        rrIntervals.size(), totalSec / 60, totalSec % 60));
                fileStatusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-style: normal;");
            } else {
                showError("Ошибка", "Не удалось найти колонку 'duration' в CSV.");
            }
        } catch (Exception ex) {
            showError("Ошибка чтения файла", ex.getMessage());
        }
    }

    private void processAnalysis() {
        saveTimeSettings();
        if (rrIntervals.isEmpty()) {
            showError("Ошибка расчета", "Сначала выберите и загрузите корректный CSV-файл.");
            return;
        }

        try {
            long tLyingStart = (Integer.parseInt(minLyingStart.getText().trim()) * 60L + Integer.parseInt(secLyingStart.getText().trim())) * 1000L;
            long tChange = (Integer.parseInt(minChange.getText().trim()) * 60L + Integer.parseInt(secChange.getText().trim())) * 1000L;
            long tStandingStart = (Integer.parseInt(minStandingStart.getText().trim()) * 60L + Integer.parseInt(secStandingStart.getText().trim())) * 1000L;
            long tEnd = totalFileDurationMs;

            if (tLyingStart >= tChange || tChange >= tStandingStart || tStandingStart >= tEnd) {
                showError("Ошибка хронологии", "Нарушена последовательность этапов! Проверьте, чтобы:\nЛёжа < Подъём < Стоя < Конец записи.");
                return;
            }

            // Маппинг временных меток на индексы массива RR
            int idxLyingStart = -1, idxChange = -1, idxStandingStart = -1;
            long currentSumMs = 0;

            for (int i = 0; i < rrIntervals.size(); i++) {
                currentSumMs += rrIntervals.get(i);
                if (idxLyingStart == -1 && currentSumMs >= tLyingStart) idxLyingStart = i;
                if (idxChange == -1 && currentSumMs >= tChange) idxChange = i;
                if (idxStandingStart == -1 && currentSumMs >= tStandingStart) {
                    idxStandingStart = i;
                    break;
                }
            }

            if (idxChange == -1 || idxChange >= rrIntervals.size() - REQUIRED_POST_CHANGE_BEATS) {
                showError("Недостаточно данных", "В файле мало данных после момента подъема для расчета коэффициента 30:15.");
                return;
            }

            // --- 0. ЗНАЧЕНИЕ В МОМЕНТ НАЧАЛА ПОДЪЕМА ---
            double startRrValue = rrIntervals.get(idxChange);

            // ==========================================
            // 1. СОВРЕМЕННЫЙ МЕДИЦИНСКИЙ РАСЧЕТ ИНДЕКСА 30:15 (ПО ОКНАМ ЮИНГА)
            // ==========================================
            int endWindow = Math.min(idxChange + REQUIRED_POST_CHANGE_BEATS, rrIntervals.size());
            List<Double> postStandingWindow = rrIntervals.subList(idxChange, endWindow);

            // Ищем minRR15 строго в окне с 5 по 25 удар
            double minRR15 = Double.MAX_VALUE;
            for (int i = MIN_RR15_WINDOW_START; i <= MIN_RR15_WINDOW_END && i < postStandingWindow.size(); i++) {
                if (postStandingWindow.get(i) < minRR15) {
                    minRR15 = postStandingWindow.get(i);
                }
            }
            // Ищем maxRR30 строго в окне с 20 по 40 удар
            double maxRR30 = 0.0;
            for (int i = MAX_RR30_WINDOW_START; i <= MAX_RR30_WINDOW_END && i < postStandingWindow.size(); i++) {
                if (postStandingWindow.get(i) > maxRR30) {
                    maxRR30 = postStandingWindow.get(i);
                }
            }
            double ratio3015 = maxRR30 / minRR15;
            // ==========================================
            // 2. РАСЧЕТ ОРТОСТАТИЧЕСКОЙ РАЗНИЦЫ (С ПУЛЕНЕПРОБИВАЕМЫМИ ЦИКЛАМИ)
            // ==========================================
            List hrValues = new ArrayList<>();
            for (double rr : rrIntervals) {
                hrValues.add(60000.0 / rr);
            }
            // Средняя ЧСС ЛЁЖА
            List hrLyingPeriod = hrValues.subList(idxLyingStart, idxChange);
            double sumLying = 0;
            for (int i = 0; i < hrLyingPeriod.size(); i++) {
                sumLying += (Double) hrLyingPeriod.get(i);
            }
            double avgLyingHr = hrLyingPeriod.isEmpty() ? 0.0 : (sumLying / hrLyingPeriod.size());
            // Средняя ЧСС СТОЯ
            List hrStandingPeriod = hrValues.subList(idxStandingStart, hrValues.size());
            double sumStanding = 0;
            for (int i = 0; i < hrStandingPeriod.size(); i++) {
                sumStanding += (Double) hrStandingPeriod.get(i);
            }
            double avgStandingHr = hrStandingPeriod.isEmpty() ? 0.0 : (sumStanding / hrStandingPeriod.size());
            double orthoDifference = avgStandingHr - avgLyingHr;
            // --- Вывод всех результатов на главный экран ---
            startRrResultLabel.setText(String.format("RR в момент начала подъема: %.0f мс", startRrValue));
            minRr15ResultLabel.setText(String.format("minRR15 (в районе 15-го удара): %.0f мс", minRR15));
            maxRr30ResultLabel.setText(String.format("maxRR30 (в районе 30-го удара): %.0f мс", maxRR30));
            orthoResultLabel.setText(String.format("Ортостатическая разница = %.1f уд/мин", orthoDifference));
            ratioResultLabel.setText(String.format("Коэффициент 30:15 = %.3f", ratio3015));
        } catch (NumberFormatException e) {
            showError("Неверный формат", "Поля времени должны содержать только целые числа.");
        } catch (Exception ex) {
            showError("Ошибка анализа", ex.getMessage());
        }
    }
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    public static void main(String[] args) {
        launch(args);
    }
}