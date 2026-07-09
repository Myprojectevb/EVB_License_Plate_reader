package main.java;

import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.scene.shape.Rectangle;
import javafx.scene.layout.HBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.stage.Stage;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import javafx.scene.Group;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.scene.shape.Circle;
import javafx.concurrent.Task;
import javafx.application.Platform;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.ByteArrayInputStream;
import java.util.Base64;

public class MainController {
    @FXML
    private StackPane adScreen;
    @FXML
    private VBox root;
    @FXML
    private StackPane adBox;

    private VBox welcomeScreen;
    private EventHandler<MouseEvent> welcomeClickHandler;
    private Timeline chargeTimerTimeline;
    private int chargeTimerSeconds = 0;
    private Label chargeTimerLabel; // reference to the timer label in the circle
    
    // Face recognition API integration
    private HttpClient httpClient;
    private ScheduledExecutorService faceCheckExecutor;
    private boolean isFaceScanning = false;
    private String currentUserName = null;
    // Plate scanning integration
    private ScheduledExecutorService plateExecutor;
    private boolean isPlateScanning = false;
    private boolean suppressFaceUiNavigation = false;
    private ImageView plateImageView;
    private Label plateTextLabel;
    private String latestPlateText = null;

    @FXML
    public void initialize() {
        // Initialize HTTP client for face recognition API
        httpClient = HttpClient.newHttpClient();
        faceCheckExecutor = Executors.newScheduledThreadPool(1);
        
        // Start face scanning when app initializes
        startFaceScanning();
        
        // Add ad video to adBox (gray box) on initial screen
        try {
            String adPath = new java.io.File("ad_example.mp4").toURI().toString();
            Media adMedia = new Media(adPath);
            MediaPlayer adPlayer = new MediaPlayer(adMedia);
            adPlayer.setCycleCount(MediaPlayer.INDEFINITE);
            adPlayer.setMute(true);
            adPlayer.play();
            MediaView adView = new MediaView(adPlayer);
            adView.setPreserveRatio(true);
            adView.setFitWidth(540);
            adView.setFitHeight(350);
            adBox.getChildren().add(0, adView); // add below badge
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Start periodic face checking
        startPeriodicFaceCheck();

        // Ensure background tasks stop when window closes
        setupLifecycleHooks();
    }

    private void showWelcomeScreen() {
        showWelcomeScreen("Rishabh"); // Default fallback
    }
    
    private void showWelcomeScreen(String userName) {
        if (welcomeScreen == null) {
            welcomeScreen = createWelcomeScreen(userName);
        } else {
            // Update the welcome screen with new user name
            welcomeScreen = createWelcomeScreen(userName);
        }
        // Remove the event filter if present
        if (root.getScene() != null && welcomeClickHandler != null) {
            root.getScene().removeEventFilter(MouseEvent.MOUSE_CLICKED, welcomeClickHandler);
        }
        root.getChildren().set(0, welcomeScreen); // Replace adScreen with welcomeScreen
    }

    private VBox createWelcomeScreen(String userName) {
        VBox card = new VBox(30);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 24; -fx-padding: 32; -fx-border-color: #3bb3a6; -fx-border-width: 3; -fx-border-radius: 24;");
        card.setAlignment(javafx.geometry.Pos.TOP_LEFT);
        card.setPrefWidth(500);
        card.setPrefHeight(1000);

        // Logo row
        HBox logoRow = new HBox(10);
        logoRow.setAlignment(javafx.geometry.Pos.TOP_LEFT);
        ImageView logo = new ImageView();
        try {
            Image logoImg = new Image(getClass().getResourceAsStream("/logo.png"));
            logo.setImage(logoImg);
            logo.setFitWidth(40);
            logo.setFitHeight(40);
        } catch (Exception e) {}
        Label evBuddy = new Label("ev buddy");
        evBuddy.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: black;");
        Label inc = new Label("INC.");
        inc.setStyle("-fx-font-size: 8; -fx-text-fill: black; -fx-translate-y: 15;");
        logoRow.getChildren().addAll(logo, evBuddy, inc);

        // Welcome text (centered)
        javafx.scene.text.Text welcomeText1 = new javafx.scene.text.Text("Welcome, ");
        welcomeText1.setStyle("-fx-font-size: 80; -fx-font-weight: normal; -fx-fill: black;");
        javafx.scene.text.Text welcomeText2 = new javafx.scene.text.Text(userName + "!");
        welcomeText2.setStyle("-fx-font-size: 80; -fx-font-weight: bold; -fx-fill: black;");
        javafx.scene.text.TextFlow welcome = new javafx.scene.text.TextFlow(welcomeText1, welcomeText2);
        welcome.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        // Center the welcome text and box
        VBox centerBox = new VBox(30);
        centerBox.setAlignment(javafx.geometry.Pos.CENTER);
        centerBox.setPrefWidth(500);
        // Green bar with text inside
        StackPane barPane = new StackPane();
        Rectangle bar = new Rectangle(300, 50);
        bar.setArcWidth(60);
        bar.setArcHeight(60);
        bar.setFill(javafx.scene.paint.Color.web("#3bb3a6"));
        bar.setEffect(new javafx.scene.effect.DropShadow(8, javafx.scene.paint.Color.rgb(0,0,0,0.12)));
        Label beginCharging = new Label("Begin Charging");
        beginCharging.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: white;");
        barPane.getChildren().addAll(bar, beginCharging);
        barPane.setOnMouseClicked(e -> showChargingDetailsScreen());
        centerBox.getChildren().addAll(welcome, barPane);
        VBox.setMargin(centerBox, new javafx.geometry.Insets(200,0,0,0));

        card.getChildren().addAll(logoRow, centerBox);
        return new VBox(card);
    }

    private void showChargingDetailsScreen() {
        // Lock UI flow so face polling cannot navigate away during charging setup
        suppressFaceUiNavigation = true;
        // Remove the event filter if present
        if (root.getScene() != null && welcomeClickHandler != null) {
            root.getScene().removeEventFilter(MouseEvent.MOUSE_CLICKED, welcomeClickHandler);
        }

        VBox mainBox = new VBox(24);
        mainBox.setStyle("-fx-background-color: white; -fx-background-radius: 24; -fx-padding: 32; -fx-border-color: #3bb3a6; -fx-border-width: 3; -fx-border-radius: 24;");
        mainBox.setPrefWidth(500);
        mainBox.setPrefHeight(900);
        mainBox.setAlignment(javafx.geometry.Pos.TOP_CENTER);

        String infoBg = "-fx-background-color: #3bb3a6; -fx-background-radius: 32;";
        String infoLabelStyle = "-fx-font-size: 32; -fx-text-fill: white; -fx-font-family: 'Inter', 'Arial', sans-serif;";
        String infoValueStyle = "-fx-font-size: 36; -fx-font-weight: bold; -fx-text-fill: #232323; -fx-font-family: 'Inter', 'Arial', sans-serif;";
        String infoValueStyleWhite = "-fx-font-size: 36; -fx-font-weight: bold; -fx-text-fill: white; -fx-font-family: 'Inter', 'Arial', sans-serif;";

        // Battery Level
        HBox batteryRow = new HBox();
        batteryRow.setStyle(infoBg);
        batteryRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        batteryRow.setSpacing(16);
        batteryRow.setPadding(new javafx.geometry.Insets(0, 24, 0, 24));
        batteryRow.setPrefHeight(64);
        batteryRow.setPrefWidth(500);
        batteryRow.setMaxWidth(500);
        batteryRow.setMinWidth(500);
        Label batteryLabel = new Label("Current Battery Level:");
        batteryLabel.setStyle(infoLabelStyle);
        Label batteryValue = new Label("23%");
        batteryValue.setStyle(infoValueStyleWhite);
        HBox.setHgrow(batteryLabel, javafx.scene.layout.Priority.ALWAYS);
        batteryRow.getChildren().addAll(batteryLabel, batteryValue);

        // Charge To
        HBox chargeToRow = new HBox();
        chargeToRow.setStyle(infoBg);
        chargeToRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        chargeToRow.setSpacing(16);
        chargeToRow.setPadding(new javafx.geometry.Insets(0, 24, 0, 24));
        chargeToRow.setPrefHeight(64);
        chargeToRow.setPrefWidth(500);
        chargeToRow.setMaxWidth(500);
        chargeToRow.setMinWidth(500);
        Label chargeToLabel = new Label("Charge To:");
        chargeToLabel.setStyle(infoLabelStyle);
        javafx.scene.control.ComboBox<String> chargeCombo = new javafx.scene.control.ComboBox<>();
        chargeCombo.getItems().addAll("50%", "60%", "70%", "80%", "90%", "100%");
        chargeCombo.setPromptText("Tap to Select...");
        chargeCombo.setPrefWidth(180);
        chargeCombo.setPrefHeight(40);
        chargeCombo.setStyle("-fx-font-size: 28; -fx-background-radius: 16; -fx-background-color: #f5f5f5; -fx-border-radius: 16; -fx-border-width: 0;");
        chargeToRow.getChildren().addAll(chargeToLabel, chargeCombo);
        HBox.setHgrow(chargeToLabel, javafx.scene.layout.Priority.ALWAYS);

        // Time to Complete
        HBox timeRow = new HBox();
        timeRow.setStyle(infoBg);
        timeRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        timeRow.setSpacing(16);
        timeRow.setPadding(new javafx.geometry.Insets(0, 24, 0, 24));
        timeRow.setPrefHeight(64);
        timeRow.setPrefWidth(500);
        timeRow.setMaxWidth(500);
        timeRow.setMinWidth(500);
        Label timeLabel = new Label("Time to Complete:");
        timeLabel.setStyle(infoLabelStyle);
        Label timeValue = new Label("");
        timeValue.setStyle(infoValueStyleWhite);
        timeRow.getChildren().addAll(timeLabel, timeValue);
        HBox.setHgrow(timeLabel, javafx.scene.layout.Priority.ALWAYS);
        // Update time to complete when chargeCombo changes
        chargeCombo.setOnAction(e -> {
            String selected = chargeCombo.getValue();
            if (selected != null && selected.endsWith("%")) {
                try {
                    int currentLevel = 23; // hardcoded for now
                    int target = Integer.parseInt(selected.replace("%", ""));
                    int time = Math.max(0, (target - currentLevel) * 2); // simple linear function
                    timeValue.setText(time + " mins");
                } catch (Exception ex) {
                    timeValue.setText("");
                }
            } else {
                timeValue.setText("");
            }
        });

        // Vehicle Model
        HBox modelRow = new HBox();
        modelRow.setStyle(infoBg);
        modelRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        modelRow.setSpacing(16);
        modelRow.setPadding(new javafx.geometry.Insets(0, 24, 0, 24));
        modelRow.setPrefHeight(64);
        modelRow.setPrefWidth(500);
        modelRow.setMaxWidth(500);
        modelRow.setMinWidth(500);
        Label modelLabel = new Label("Vehicle Model:");
        modelLabel.setStyle(infoLabelStyle);
        Label modelValue = new Label("Rivian R1T");
        modelValue.setStyle(infoValueStyleWhite);
        modelRow.getChildren().addAll(modelLabel, modelValue);
        HBox.setHgrow(modelLabel, javafx.scene.layout.Priority.ALWAYS);

        // Payment Button
        Button payButton = new Button("Pay with Card Ending in 3492");
        payButton.setStyle("-fx-background-color: #256862; -fx-background-radius: 32; -fx-font-size: 32; -fx-font-weight: normal; -fx-text-fill: white; -fx-padding: 16 0; -fx-font-family: 'Inter', 'Arial', sans-serif;");
        payButton.setPrefHeight(64);
        payButton.setPrefWidth(500);
        payButton.setMaxWidth(500);
        payButton.setMinWidth(500);

        // Alternate payment text
        Label altPay = new Label("Tap Here to Select Alternate Payment Method");
        altPay.setStyle("-fx-font-size: 20; -fx-font-family: 'Inter', 'Arial', sans-serif; -fx-text-fill: #256862; -fx-font-weight: bold; -fx-padding: 12 0 24 0;");
        altPay.setAlignment(javafx.geometry.Pos.CENTER);
        altPay.setPrefWidth(500);
        altPay.setMaxWidth(500);
        altPay.setMinWidth(500);

        // Add all rows to mainBox
        mainBox.getChildren().addAll(batteryRow, chargeToRow, timeRow, modelRow, payButton, altPay);

        // Keep the START button as is, centered at the bottom
        VBox buttonBox = new VBox();
        buttonBox.setPrefWidth(450);
        buttonBox.setPrefHeight(200);
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER);
        Button startButton = new Button("START");
        startButton.setStyle("-fx-background-color: #3bb3a6; -fx-background-radius: 24; -fx-font-size: 36; -fx-text-fill: white; -fx-font-weight: bold;");
        startButton.setPrefWidth(200);
        startButton.setPrefHeight(80);
        VBox.setVgrow(startButton, javafx.scene.layout.Priority.ALWAYS);
        startButton.setOnAction(e -> showAdAndTimerScreen());
        buttonBox.getChildren().setAll(startButton);
        mainBox.getChildren().add(buttonBox);

        root.getChildren().set(0, mainBox);
    }

    // New method for ad/timer page
    private void showAdAndTimerScreen() {
        // Keep UI locked during active charging session
        suppressFaceUiNavigation = true;
        VBox mainSplit = new VBox(30);
        mainSplit.setPrefWidth(500);
        mainSplit.setPrefHeight(1000);
        mainSplit.setStyle("-fx-background-color: white; -fx-background-radius: 24; -fx-padding: 32; -fx-border-color: #3bb3a6; -fx-border-width: 3; -fx-border-radius: 24;");
        // Top: Circular charging bar
        VBox topBox = new VBox();
        topBox.setAlignment(javafx.geometry.Pos.CENTER);
        topBox.setPrefWidth(500);
        topBox.setPrefHeight(400);
        StackPane circlePane = new StackPane();
        circlePane.setPrefWidth(300);
        circlePane.setPrefHeight(300);
        circlePane.setMinSize(300, 300);
        circlePane.setMaxSize(300, 300);
        Group circleGroup = new Group();
        double center = 150;
        double radius = 120;
        double strokeWidth = 18;
        javafx.scene.shape.Circle bgCircle = new javafx.scene.shape.Circle(center, center, radius);
        bgCircle.setStroke(javafx.scene.paint.Color.web("#e0e0e0"));
        bgCircle.setStrokeWidth(strokeWidth);
        bgCircle.setFill(javafx.scene.paint.Color.TRANSPARENT);
        javafx.scene.shape.Arc progressArc = new javafx.scene.shape.Arc(center, center, radius, radius, 90, -180); // 50% for demo
        progressArc.setType(javafx.scene.shape.ArcType.OPEN);
        progressArc.setStroke(javafx.scene.paint.Color.web("#3bb3a6"));
        progressArc.setStrokeWidth(strokeWidth);
        progressArc.setFill(javafx.scene.paint.Color.TRANSPARENT);
        progressArc.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.BUTT);
        circleGroup.getChildren().addAll(bgCircle, progressArc);
        VBox centerText = new VBox(0);
        centerText.setAlignment(javafx.geometry.Pos.CENTER);
        centerText.setPrefWidth(300);
        centerText.setPrefHeight(300);
        centerText.setMinSize(300, 300);
        centerText.setMaxSize(300, 300);
        chargeTimerLabel = new Label("0:00");
        chargeTimerLabel.setStyle("-fx-font-size: 48; -fx-text-fill: #256862; -fx-font-family: 'Inter', 'Arial', sans-serif;");
        Label percent = new Label("XX%");
        percent.setStyle("-fx-font-size: 24; -fx-text-fill: #256862; -fx-font-family: 'Inter', 'Arial', sans-serif;");
        centerText.getChildren().addAll(chargeTimerLabel, percent);
        circlePane.getChildren().addAll(circleGroup, centerText);
        topBox.getChildren().add(circlePane);

        // Bottom: Ad area
        StackPane adPane = new StackPane();
        adPane.setPrefWidth(500);
        adPane.setPrefHeight(500);
        adPane.setStyle("-fx-background-color: #e0e0e0; -fx-background-radius: 16; -fx-border-color: #3bb3a6; -fx-border-width: 2; -fx-border-radius: 16;");
        // Play ad video in the adPane
        String adPath = new java.io.File("ad_example.mp4").toURI().toString();
        Media adMedia = new Media(adPath);
        MediaPlayer adPlayer = new MediaPlayer(adMedia);
        adPlayer.setCycleCount(MediaPlayer.INDEFINITE);
        adPlayer.setMute(true);
        adPlayer.play();
        MediaView adView = new MediaView(adPlayer);
        adView.setPreserveRatio(true);
        adView.fitWidthProperty().bind(adPane.prefWidthProperty());
        adView.fitHeightProperty().bind(adPane.prefHeightProperty());
        adPane.getChildren().add(adView);

        mainSplit.getChildren().setAll(topBox, adPane);
        VBox wrapper = new VBox(mainSplit);
        wrapper.setAlignment(javafx.geometry.Pos.CENTER);
        wrapper.setPrefWidth(600);
        wrapper.setPrefHeight(1200);
        root.getChildren().set(0, wrapper);

        // Start the real-time charge timer
        startChargeTimer();
    }

    // New: Tap to Get Started screen
    private void showTapToGetStartedScreen() {
        VBox wrapper = new VBox();
        wrapper.setAlignment(javafx.geometry.Pos.CENTER);
        wrapper.setPrefWidth(600);
        wrapper.setPrefHeight(900);
        wrapper.setStyle("-fx-background-color: white;");

        // Logo row (reuse from welcome screen)
        HBox logoRow = new HBox(10);
        logoRow.setAlignment(javafx.geometry.Pos.TOP_LEFT);
        ImageView logo = new ImageView();
        try {
            Image logoImg = new Image(getClass().getResourceAsStream("/logo.png"));
            logo.setImage(logoImg);
            logo.setFitWidth(40);
            logo.setFitHeight(40);
        } catch (Exception e) {}
        Label evBuddy = new Label("ev buddy");
        evBuddy.setStyle("-fx-font-size: 32; -fx-font-weight: bold; -fx-text-fill: black;");
        Label inc = new Label("INC.");
        inc.setStyle("-fx-font-size: 10; -fx-text-fill: black; -fx-translate-y: 10;");
        logoRow.getChildren().addAll(logo, evBuddy, inc);
        VBox.setMargin(logoRow, new javafx.geometry.Insets(24, 0, 0, 24));

        // Large circle with text
        StackPane circlePane = new StackPane();
        circlePane.setPrefWidth(400);
        circlePane.setPrefHeight(400);
        Circle circle = new Circle(200);
        circle.setFill(javafx.scene.paint.Color.web("#3bb3a6"));
        circle.setEffect(new javafx.scene.effect.DropShadow(16, javafx.scene.paint.Color.rgb(0,0,0,0.12)));
        Label tapLabel = new Label("Tap to\nGet\nStarted");
        tapLabel.setStyle("-fx-font-size: 56; -fx-text-fill: white; -fx-font-family: 'Inter', 'Arial', sans-serif; -fx-alignment: center;");
        tapLabel.setAlignment(javafx.geometry.Pos.CENTER);
        tapLabel.setWrapText(true);
        circlePane.getChildren().addAll(circle, tapLabel);
        VBox.setMargin(circlePane, new javafx.geometry.Insets(60, 0, 0, 0));
        // Make the circle clickable
        circlePane.setOnMouseClicked(e -> showLicensePlateScanScreen());

        wrapper.getChildren().addAll(logoRow, circlePane);
        root.getChildren().set(0, wrapper);
    }

    // New: Live License Plate Scan screen
    private void showLicensePlateScanScreen() {
        // Stop face scanning UI flow and start plate scanning
        suppressFaceUiNavigation = true;
        stopFacePolling();
        startPlateScanning();

        VBox mainBox = new VBox(16);
        mainBox.setStyle("-fx-background-color: white; -fx-background-radius: 24; -fx-padding: 24; -fx-border-color: #3bb3a6; -fx-border-width: 3; -fx-border-radius: 24;");
        mainBox.setPrefWidth(540);
        mainBox.setPrefHeight(900);
        mainBox.setAlignment(javafx.geometry.Pos.TOP_CENTER);

        Label title = new Label("Scan Your License Plate");
        title.setStyle("-fx-font-size: 28; -fx-font-weight: bold; -fx-text-fill: #232323;");

        // Live feed container
        StackPane videoPane = new StackPane();
        videoPane.setStyle("-fx-background-color: #000000; -fx-background-radius: 12;");
        videoPane.setPrefWidth(500);
        videoPane.setPrefHeight(320);
        plateImageView = new ImageView();
        plateImageView.setPreserveRatio(true);
        plateImageView.setFitWidth(500);
        plateImageView.setFitHeight(320);
        videoPane.getChildren().add(plateImageView);

        // Current reading label
        plateTextLabel = new Label("Reading: —");
        plateTextLabel.setStyle("-fx-font-size: 22; -fx-text-fill: #256862; -fx-font-family: 'Inter', 'Arial', sans-serif;");

        // Hint text
        Label hint = new Label("Position your plate in view. We'll read it automatically.");
        hint.setStyle("-fx-font-size: 16; -fx-text-fill: #555555;");

        Button proceedBtn = new Button("Proceed to Face Registration");
        proceedBtn.setStyle("-fx-background-color: #256862; -fx-background-radius: 24; -fx-font-size: 22; -fx-text-fill: white; -fx-padding: 12 24;");
        proceedBtn.setOnAction(e -> showFaceRegistrationScreen());

        mainBox.getChildren().addAll(title, videoPane, plateTextLabel, hint, proceedBtn);
        root.getChildren().set(0, mainBox);
    }

    // Original: Manual Register License Plate screen (kept for future use)
    private void showRegisterLicensePlateScreen() {
        VBox mainBox = new VBox(32);
        mainBox.setStyle("-fx-background-color: white; -fx-background-radius: 24; -fx-padding: 32; -fx-border-color: #3bb3a6; -fx-border-width: 3; -fx-border-radius: 24;");
        mainBox.setPrefWidth(500);
        mainBox.setPrefHeight(900);
        mainBox.setAlignment(javafx.geometry.Pos.TOP_CENTER);

        String infoBg = "-fx-background-color: #3bb3a6; -fx-background-radius: 32;";
        String infoLabelStyle = "-fx-font-size: 28; -fx-text-fill: white; -fx-font-family: 'Inter', 'Arial', sans-serif;";
        String infoValueStyle = "-fx-font-size: 28; -fx-font-weight: normal; -fx-text-fill: #232323; -fx-font-family: 'Inter', 'Arial', sans-serif;";
        String infoValueStyleWhite = "-fx-font-size: 28; -fx-font-weight: bold; -fx-text-fill: white; -fx-font-family: 'Inter', 'Arial', sans-serif;";

        // Helper for popup keypad
        java.util.function.Consumer<TextField> attachKeypad = (field) -> {
        Popup keypadPopup = new Popup();
            VBox keyboard = new VBox(4);
            keyboard.setStyle("-fx-background-color: white; -fx-padding: 10; -fx-border-color: #3bb3a6; -fx-border-width: 2; -fx-background-radius: 12; -fx-border-radius: 12;");
            StringBuilder inputBuilder = new StringBuilder(field.getText());
            String[] rows = {
                "1234567890",
                "QWERTYUIOP",
                "ASDFGHJKL",
                "ZXCVBNM"
            };
            for (String row : rows) {
                HBox rowBox = new HBox(4);
                for (char c : row.toCharArray()) {
                    Button btn = new Button(String.valueOf(c));
                    btn.setPrefSize(44, 44);
                    btn.setStyle("-fx-font-size: 20;");
                btn.setOnAction(e -> {
                        inputBuilder.append(btn.getText());
                        field.setText(inputBuilder.toString());
                    });
                    rowBox.getChildren().add(btn);
                }
                keyboard.getChildren().add(rowBox);
            }
            // Last row: space, backspace, clear
            HBox lastRow = new HBox(4);
            Button spaceBtn = new Button("SPACE");
            spaceBtn.setPrefSize(88, 44);
            spaceBtn.setStyle("-fx-font-size: 18;");
            spaceBtn.setOnAction(e -> {
                inputBuilder.append(" ");
                field.setText(inputBuilder.toString());
            });
            Button backBtn = new Button("←");
            backBtn.setPrefSize(44, 44);
            backBtn.setStyle("-fx-font-size: 20;");
            backBtn.setOnAction(e -> {
                if (inputBuilder.length() > 0) {
                    inputBuilder.setLength(inputBuilder.length() - 1);
                    field.setText(inputBuilder.toString());
                }
            });
        Button clearBtn = new Button("C");
            clearBtn.setPrefSize(44, 44);
            clearBtn.setStyle("-fx-font-size: 20;");
        clearBtn.setOnAction(e -> {
            inputBuilder.setLength(0);
                field.setText("");
            });
            lastRow.getChildren().addAll(spaceBtn, backBtn, clearBtn);
            keyboard.getChildren().add(lastRow);
            keypadPopup.getContent().add(keyboard);
        // Hide keypad when clicking outside
        EventHandler<MouseEvent> hideKeypadHandler = new EventHandler<>() {
            @Override
            public void handle(MouseEvent event) {
                    if (!field.isHover() && !keyboard.isHover()) {
                    keypadPopup.hide();
                        if (field.getScene() != null) {
                            field.getScene().removeEventFilter(MouseEvent.MOUSE_PRESSED, this);
                        }
                    }
                }
            };
            field.setOnMouseClicked(e -> {
            if (!keypadPopup.isShowing()) {
                    inputBuilder.setLength(0);
                    inputBuilder.append(field.getText());
                    keypadPopup.show(field, field.localToScreen(0, field.getHeight()).getX(), field.localToScreen(0, field.getHeight()).getY());
                    if (field.getScene() != null) {
                        field.getScene().addEventFilter(MouseEvent.MOUSE_PRESSED, hideKeypadHandler);
                }
            }
        });
        // Hide keypad when focus lost (for keyboard navigation)
            field.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) keypadPopup.hide();
        });
        };

        // Plate Number
        HBox plateRow = new HBox();
        plateRow.setStyle(infoBg);
        plateRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        plateRow.setSpacing(16);
        plateRow.setPadding(new javafx.geometry.Insets(8, 32, 8, 32));
        plateRow.setPrefHeight(72);
        plateRow.setPrefWidth(500);
        plateRow.setMaxWidth(500);
        plateRow.setMinWidth(500);
        Label plateLabel = new Label("Plate Number:");
        plateLabel.setStyle(infoLabelStyle);
        TextField plateField = new TextField();
        plateField.setPromptText("Tap to Type...");
        plateField.setPrefWidth(220);
        plateField.setPrefHeight(44);
        plateField.setStyle("-fx-font-size: 24; -fx-background-radius: 16; -fx-background-color: #f5f5f5; -fx-border-radius: 16; -fx-border-width: 0;");
        plateField.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        plateRow.getChildren().addAll(plateLabel, plateField);
        HBox.setHgrow(plateLabel, javafx.scene.layout.Priority.ALWAYS);
        attachKeypad.accept(plateField);

        // US State of Registration
        HBox stateRow = new HBox();
        stateRow.setStyle(infoBg);
        stateRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        stateRow.setSpacing(16);
        stateRow.setPadding(new javafx.geometry.Insets(8, 32, 8, 32));
        stateRow.setPrefHeight(72);
        stateRow.setPrefWidth(500);
        stateRow.setMaxWidth(500);
        stateRow.setMinWidth(500);
        Label stateLabel = new Label("US State of Registration:");
        stateLabel.setStyle(infoLabelStyle);
        javafx.scene.control.ComboBox<String> stateCombo = new javafx.scene.control.ComboBox<>();
        stateCombo.setPromptText("Tap to Select...");
        stateCombo.setPrefWidth(220);
        stateCombo.setPrefHeight(44);
        stateCombo.setStyle("-fx-font-size: 24; -fx-background-radius: 16; -fx-background-color: #f5f5f5; -fx-border-radius: 16; -fx-border-width: 0;");
        stateCombo.getItems().addAll(
            "Alabama", "Alaska", "Arizona", "Arkansas", "California", "Colorado", "Connecticut", "Delaware", "Florida", "Georgia",
            "Hawaii", "Idaho", "Illinois", "Indiana", "Iowa", "Kansas", "Kentucky", "Louisiana", "Maine", "Maryland",
            "Massachusetts", "Michigan", "Minnesota", "Mississippi", "Missouri", "Montana", "Nebraska", "Nevada", "New Hampshire", "New Jersey",
            "New Mexico", "New York", "North Carolina", "North Dakota", "Ohio", "Oklahoma", "Oregon", "Pennsylvania", "Rhode Island", "South Carolina",
            "South Dakota", "Tennessee", "Texas", "Utah", "Vermont", "Virginia", "Washington", "West Virginia", "Wisconsin", "Wyoming"
        );
        stateRow.getChildren().addAll(stateLabel, stateCombo);
        HBox.setHgrow(stateLabel, javafx.scene.layout.Priority.ALWAYS);

        // Identification Number (VIN/HIN)
        HBox idRow = new HBox();
        idRow.setStyle(infoBg);
        idRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        idRow.setSpacing(16);
        idRow.setPadding(new javafx.geometry.Insets(8, 32, 8, 32));
        idRow.setPrefHeight(72);
        idRow.setPrefWidth(500);
        idRow.setMaxWidth(500);
        idRow.setMinWidth(500);
        VBox idLabelBox = new VBox(0);
        Label idLabel = new Label("Identification Number:");
        idLabel.setStyle(infoLabelStyle);
        Label idSub = new Label("(VIN / HIN)");
        idSub.setStyle("-fx-font-size: 13; -fx-text-fill: white; -fx-font-family: 'Inter', 'Arial', sans-serif; -fx-padding: 0 0 0 0;");
        idLabelBox.getChildren().addAll(idLabel, idSub);
        TextField idField = new TextField();
        idField.setPromptText("Tap to Type...");
        idField.setPrefWidth(220);
        idField.setPrefHeight(44);
        idField.setStyle("-fx-font-size: 24; -fx-background-radius: 16; -fx-background-color: #f5f5f5; -fx-border-radius: 16; -fx-border-width: 0;");
        idField.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        attachKeypad.accept(idField);
        idRow.getChildren().addAll(idLabelBox, idField);
        HBox.setHgrow(idLabelBox, javafx.scene.layout.Priority.ALWAYS);

        // Vehicle Model (now a TextField)
        HBox modelRow = new HBox();
        modelRow.setStyle(infoBg);
        modelRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        modelRow.setSpacing(16);
        modelRow.setPadding(new javafx.geometry.Insets(8, 32, 8, 32));
        modelRow.setPrefHeight(72);
        modelRow.setPrefWidth(500);
        modelRow.setMaxWidth(500);
        modelRow.setMinWidth(500);
        Label modelLabel = new Label("Vehicle Model:");
        modelLabel.setStyle(infoLabelStyle);
        TextField modelField = new TextField();
        modelField.setPromptText("Tap to Type...");
        modelField.setPrefWidth(220);
        modelField.setPrefHeight(44);
        modelField.setStyle("-fx-font-size: 24; -fx-background-radius: 16; -fx-background-color: #f5f5f5; -fx-border-radius: 16; -fx-border-width: 0;");
        modelField.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        modelRow.getChildren().addAll(modelLabel, modelField);
        HBox.setHgrow(modelLabel, javafx.scene.layout.Priority.ALWAYS);
        attachKeypad.accept(modelField);

        // Register License Plate Button
        Button registerButton = new Button("Register License Plate");
        registerButton.setStyle("-fx-background-color: #256862; -fx-background-radius: 32; -fx-font-size: 28; -fx-font-weight: normal; -fx-text-fill: white; -fx-padding: 16 0; -fx-font-family: 'Inter', 'Arial', sans-serif;");
        registerButton.setPrefHeight(64);
        registerButton.setPrefWidth(500);
        registerButton.setMaxWidth(500);
        registerButton.setMinWidth(500);
        registerButton.setOnAction(e -> showPaymentInfoScreen());

        mainBox.getChildren().addAll(plateRow, stateRow, idRow, modelRow, registerButton);
        root.getChildren().set(0, mainBox);
    }

    // New: Payment Info Screen
    private void showPaymentInfoScreen() {
        VBox mainBox = new VBox(24);
        mainBox.setStyle("-fx-background-color: white; -fx-background-radius: 24; -fx-padding: 32; -fx-border-color: #3bb3a6; -fx-border-width: 3; -fx-border-radius: 24;");
        mainBox.setPrefWidth(500);
        mainBox.setPrefHeight(800);
        mainBox.setAlignment(javafx.geometry.Pos.TOP_CENTER);

        // Card Number (full width)
        TextField cardField = new TextField();
        cardField.setPromptText("Card Number");
        cardField.setStyle("-fx-font-size: 22; -fx-background-radius: 16; -fx-border-radius: 16; -fx-border-color: #3bb3a6; -fx-border-width: 1; -fx-background-color: #f5f5f5; -fx-padding: 0 0 0 16;");
        cardField.setPrefHeight(48);
        cardField.setPrefWidth(500);
        cardField.setMinWidth(500);
        cardField.setMaxWidth(500);

        // CVC and Date (half width, side by side)
        HBox cvcDateRow = new HBox(16);
        cvcDateRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        cvcDateRow.setPrefWidth(500);
        cvcDateRow.setMinWidth(500);
        cvcDateRow.setMaxWidth(500);
        TextField cvcField = new TextField();
        cvcField.setPromptText("CVC");
        cvcField.setStyle("-fx-font-size: 22; -fx-background-radius: 16; -fx-border-radius: 16; -fx-border-color: #3bb3a6; -fx-border-width: 1; -fx-background-color: #f5f5f5; -fx-padding: 0 0 0 16;");
        cvcField.setPrefHeight(48);
        cvcField.setPrefWidth(242);
        cvcField.setMinWidth(242);
        cvcField.setMaxWidth(242);
        TextField dateField = new TextField();
        dateField.setPromptText("MM / YY");
        dateField.setStyle("-fx-font-size: 22; -fx-background-radius: 16; -fx-border-radius: 16; -fx-border-color: #3bb3a6; -fx-border-width: 1; -fx-background-color: #f5f5f5; -fx-padding: 0 0 0 16;");
        dateField.setPrefHeight(48);
        dateField.setPrefWidth(242);
        dateField.setMinWidth(242);
        dateField.setMaxWidth(242);
        cvcDateRow.getChildren().setAll(cvcField, dateField);

        // Full Name (full width)
        TextField nameField = new TextField();
        nameField.setPromptText("Full Name on Card");
        nameField.setStyle("-fx-font-size: 22; -fx-background-radius: 16; -fx-border-radius: 16; -fx-border-color: #3bb3a6; -fx-border-width: 1; -fx-background-color: #f5f5f5; -fx-padding: 0 0 0 16;");
        nameField.setPrefHeight(48);
        nameField.setPrefWidth(500);
        nameField.setMinWidth(500);
        nameField.setMaxWidth(500);

        // Country (full width)
        ComboBox<String> countryCombo = new ComboBox<>();
        countryCombo.getItems().addAll(
            "United States", "Canada", "Mexico", "United Kingdom", "Germany", "France", "Italy", "Spain", "Australia", "India", "China", "Japan", "South Korea", "Brazil", "South Africa"
        );
        countryCombo.setValue("United States");
        countryCombo.setStyle("-fx-font-size: 22; -fx-background-radius: 16; -fx-border-radius: 16; -fx-border-color: #3bb3a6; -fx-border-width: 1; -fx-background-color: #f5f5f5; -fx-padding: 0 0 0 16;");
        countryCombo.setPrefHeight(48);
        countryCombo.setPrefWidth(500);
        countryCombo.setMinWidth(500);
        countryCombo.setMaxWidth(500);

        // Street Address (full width)
        TextField streetField = new TextField();
        streetField.setPromptText("Street Address");
        streetField.setStyle("-fx-font-size: 22; -fx-background-radius: 16; -fx-border-radius: 16; -fx-border-color: #3bb3a6; -fx-border-width: 1; -fx-background-color: #f5f5f5; -fx-padding: 0 0 0 16;");
        streetField.setPrefHeight(48);
        streetField.setPrefWidth(500);
        streetField.setMinWidth(500);
        streetField.setMaxWidth(500);

        // State and Zip (half width, side by side)
        HBox stateZipRow = new HBox(16);
        stateZipRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        stateZipRow.setPrefWidth(500);
        stateZipRow.setMinWidth(500);
        stateZipRow.setMaxWidth(500);
        TextField stateField = new TextField();
        stateField.setPromptText("State");
        stateField.setStyle("-fx-font-size: 22; -fx-background-radius: 16; -fx-border-radius: 16; -fx-border-color: #3bb3a6; -fx-border-width: 1; -fx-background-color: #f5f5f5; -fx-padding: 0 0 0 16;");
        stateField.setPrefHeight(48);
        stateField.setPrefWidth(242);
        stateField.setMinWidth(242);
        stateField.setMaxWidth(242);
        TextField zipField = new TextField();
        zipField.setPromptText("Zip Code");
        zipField.setStyle("-fx-font-size: 22; -fx-background-radius: 16; -fx-border-radius: 16; -fx-border-color: #3bb3a6; -fx-border-width: 1; -fx-background-color: #f5f5f5; -fx-padding: 0 0 0 16;");
        zipField.setPrefHeight(48);
        zipField.setPrefWidth(242);
        zipField.setMinWidth(242);
        zipField.setMaxWidth(242);
        stateZipRow.getChildren().setAll(stateField, zipField);

        // Submit button styled like other pages
        Button submitButton = new Button("Submit Payment Method");
        submitButton.setStyle("-fx-background-color: #256862; -fx-background-radius: 32; -fx-font-size: 28; -fx-font-weight: normal; -fx-text-fill: white; -fx-padding: 16 0; -fx-font-family: 'Inter', 'Arial', sans-serif;");
        submitButton.setPrefHeight(64);
        submitButton.setPrefWidth(500);
        submitButton.setMaxWidth(500);
        submitButton.setMinWidth(500);
        submitButton.setOnAction(e -> {
            // After payment, resume normal flow and go to charge config
            suppressFaceUiNavigation = false;
            resumeFacePolling();
            startFaceScanning();
            showChargingDetailsScreen();
        });

        mainBox.getChildren().setAll(cardField, cvcDateRow, nameField, countryCombo, streetField, stateZipRow, submitButton);
        root.getChildren().set(0, mainBox);
    }

    private void startChargeTimer() {
        if (chargeTimerTimeline != null) {
            chargeTimerTimeline.stop();
        }
        chargeTimerSeconds = 0;
        updateChargeTimerLabel();
        chargeTimerTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            chargeTimerSeconds++;
            updateChargeTimerLabel();
        }));
        chargeTimerTimeline.setCycleCount(Timeline.INDEFINITE);
        chargeTimerTimeline.play();
    }

    private void updateChargeTimerLabel() {
        int minutes = chargeTimerSeconds / 60;
        int seconds = chargeTimerSeconds % 60;
        String timeStr = String.format("%d:%02d", minutes, seconds);
        if (chargeTimerLabel != null) {
            chargeTimerLabel.setText(timeStr);
        }
    }

    private javafx.scene.text.Text styledBoldText(String value, int fontSize) {
        javafx.scene.text.Text t = new javafx.scene.text.Text(value);
        t.setStyle("-fx-font-size: " + fontSize + "; -fx-font-weight: bold; -fx-fill: black;");
        return t;
    }

    private javafx.scene.text.Text styledNormalText(String value, int fontSize) {
        javafx.scene.text.Text t = new javafx.scene.text.Text(value);
        t.setStyle("-fx-font-size: " + fontSize + "; -fx-font-weight: normal; -fx-fill: black;");
        return t;
    }
    
    // Face Recognition API Methods
    private void startFaceScanning() {
        Task<Void> startScanTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:5001/start-scanning"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println("Face scanning started: " + response.body());
                return null;
            }
        };
        
        startScanTask.setOnSucceeded(e -> {
            isFaceScanning = true;
            System.out.println("Face scanning is now active");
        });
        
        startScanTask.setOnFailed(e -> {
            System.err.println("Failed to start face scanning: " + startScanTask.getException());
        });
        
        new Thread(startScanTask).start();
    }
    
    private void startPeriodicFaceCheck() {
        faceCheckExecutor.scheduleAtFixedRate(() -> {
            try {
                if (isPlateScanning || suppressFaceUiNavigation) {
                    return; // Do not update face UI while plate scanning is active
                }
                checkForFaceRecognition();
            } catch (Exception e) {
                System.err.println("Error checking face recognition: " + e.getMessage());
            }
        }, 1, 2, TimeUnit.SECONDS); // Check every 2 seconds
    }
    
    private void checkForFaceRecognition() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:5001/get-current-user"))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject jsonResponse = new JSONObject(response.body());
            
            if (jsonResponse.getString("status").equals("success")) {
                boolean isRegistered = jsonResponse.getBoolean("is_registered");
                boolean facePresent = jsonResponse.optBoolean("face_present", false);
                
                Platform.runLater(() -> {
                    if (isPlateScanning || suppressFaceUiNavigation) {
                        return; // Don't let face polling navigate UI while on plate screen
                    }
                    if (isRegistered && facePresent && currentUserName == null) {
                        // New registered user detected
                        JSONObject user = jsonResponse.getJSONObject("user");
                        currentUserName = user.getString("name");
                        System.out.println("Registered user detected: " + currentUserName);
                        showWelcomeScreen(currentUserName);
                    } else if (!isRegistered && currentUserName != null) {
                        // User no longer detected
                        currentUserName = null;
                        System.out.println("User no longer detected");
                    } else if (!isRegistered && currentUserName == null) {
                        // Unregistered user detected
                        if (facePresent) {
                            System.out.println("Unregistered face present: prompting to get started");
                            showTapToGetStartedScreen();
                        } else {
                            // No face present; stay on ad screen (do nothing)
                            System.out.println("No face present; remaining on ad screen");
                        }
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Error checking face recognition: " + e.getMessage());
        }
    }

    private void stopFacePolling() {
        try {
            if (faceCheckExecutor != null) {
                faceCheckExecutor.shutdownNow();
            }
        } catch (Exception ignored) {}
    }

    // Plate Scanning API Methods
    private void startPlateScanning() {
        if (isPlateScanning) return;

        Task<Void> startPlateTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                // Probe cameras first, pick a working one if available
                try {
                    HttpRequest scanReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:5001/camera-scan"))
                        .GET()
                        .build();
                    HttpResponse<String> scanResp = httpClient.send(scanReq, HttpResponse.BodyHandlers.ofString());
                    if (scanResp.statusCode() == 200) {
                        JSONObject scanJson = new JSONObject(scanResp.body());
                        if (scanJson.has("results")) {
                            for (Object o : scanJson.getJSONArray("results")) {
                                if (!(o instanceof JSONObject)) continue;
                                JSONObject r = (JSONObject) o;
                                boolean opened = r.optBoolean("opened", false);
                                boolean readOk = r.optBoolean("read_ok", false);
                                if (opened || readOk) {
                                    int idx = r.optInt("index", -1);
                                    String backend = r.optString("backend", "");
                                    String payload = backend != null && backend.toLowerCase().contains("avfoundation")
                                        ? new JSONObject().put("index", idx).put("backend", "avfoundation").toString()
                                        : new JSONObject().put("index", idx).toString();
                                    HttpRequest startWithIdx = HttpRequest.newBuilder()
                                        .uri(URI.create("http://localhost:5001/start-plate-scanning"))
                                        .header("Content-Type", "application/json")
                                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                                        .build();
                                    httpClient.send(startWithIdx, HttpResponse.BodyHandlers.ofString());
                                    isPlateScanning = true;
                                    return null;
                                }
                            }
                        }
                    }
                } catch (Exception ignore) {}

                // Fallback: start without specifying index
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:5001/start-plate-scanning"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                isPlateScanning = true;
                return null;
            }
        };

        startPlateTask.setOnSucceeded(e -> {
            isPlateScanning = true;
            try {
                // Verify camera_ready
                HttpRequest checkReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:5001/plate-reading"))
                    .GET()
                    .build();
                httpClient.sendAsync(checkReq, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(resp -> {
                        startPlatePolling();
                    });
            } catch (Exception ex) {
                startPlatePolling();
            }
        });

        startPlateTask.setOnFailed(e -> {
            System.err.println("Failed to start plate scanning: " + startPlateTask.getException());
        });

        new Thread(startPlateTask).start();
    }

    private void setupLifecycleHooks() {
        // Attach a listener to run cleanup when the window closes
        root.sceneProperty().addListener((obsScene, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.windowProperty().addListener((obsWindow, oldWindow, newWindow) -> {
                    if (newWindow != null) {
                        ((Stage) newWindow).setOnCloseRequest(e -> cleanupAndShutdown());
                    }
                });
            }
        });
    }

    private void cleanupAndShutdown() {
        // Stop periodic executors
        try {
            if (faceCheckExecutor != null) {
                faceCheckExecutor.shutdownNow();
            }
        } catch (Exception ignored) {}
        try {
            if (plateExecutor != null) {
                plateExecutor.shutdownNow();
            }
        } catch (Exception ignored) {}

        suppressFaceUiNavigation = true; // lock during shutdown

        // Stop timers
        try {
            if (chargeTimerTimeline != null) {
                chargeTimerTimeline.stop();
            }
        } catch (Exception ignored) {}

        // Tell API to stop scanning
        try {
            HttpRequest stopFace = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:5001/stop-scanning"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
            httpClient.sendAsync(stopFace, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {}

        try {
            HttpRequest stopPlate = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:5001/stop-plate-scanning"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
            httpClient.sendAsync(stopPlate, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {}
    }

    private void startPlatePolling() {
        if (plateExecutor == null) {
            plateExecutor = Executors.newScheduledThreadPool(1);
        }

        // Poll latest JPEG frame ~30-40 FPS and reading text every 500ms
        plateExecutor.scheduleAtFixedRate(() -> {
            try {
                // Fetch frame
                HttpRequest frameReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:5001/plate-frame"))
                    .GET()
                    .build();
                HttpResponse<byte[]> frameResp = httpClient.send(frameReq, HttpResponse.BodyHandlers.ofByteArray());
                if (frameResp.statusCode() == 200 && plateImageView != null && frameResp.body() != null && frameResp.body().length > 0) {
                    byte[] jpgBytes = frameResp.body();
                    Image fxImg = new Image(new ByteArrayInputStream(jpgBytes));
                    Platform.runLater(() -> plateImageView.setImage(fxImg));
                }
            } catch (Exception ignore) { }
        }, 0, 25, TimeUnit.MILLISECONDS);

        plateExecutor.scheduleAtFixedRate(() -> {
            try {
                HttpRequest textReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:5001/plate-reading"))
                    .GET()
                    .build();
                HttpResponse<String> textResp = httpClient.send(textReq, HttpResponse.BodyHandlers.ofString());
                if (textResp.statusCode() == 200 && plateTextLabel != null) {
                    JSONObject json = new JSONObject(textResp.body());
                    String plateText = (!json.has("plate_text") || json.isNull("plate_text")) ? null : json.getString("plate_text");
                    if (plateText != null && !plateText.isBlank()) {
                        latestPlateText = plateText;
                        Platform.runLater(() -> plateTextLabel.setText("Reading: " + plateText.toUpperCase()));
                    } else if (latestPlateText != null) {
                        final String cached = latestPlateText;
                        Platform.runLater(() -> plateTextLabel.setText("Reading: " + cached.toUpperCase()));
                    } else {
                        Platform.runLater(() -> plateTextLabel.setText("Reading: —"));
                    }
                }
            } catch (Exception ignore) { }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void showFaceRegistrationScreen() {
        // Stay in plate/registration flow and prevent face polling navigation
        suppressFaceUiNavigation = true;
        VBox mainBox = new VBox(16);
        mainBox.setStyle("-fx-background-color: white; -fx-background-radius: 24; -fx-padding: 24; -fx-border-color: #3bb3a6; -fx-border-width: 3; -fx-border-radius: 24;");
        mainBox.setPrefWidth(540);
        mainBox.setPrefHeight(900);
        mainBox.setAlignment(javafx.geometry.Pos.TOP_CENTER);

        Label title = new Label("Register Your Face");
        title.setStyle("-fx-font-size: 28; -fx-font-weight: bold; -fx-text-fill: #232323;");

        // Reuse the live video feed (same camera stream)
        StackPane videoPane = new StackPane();
        videoPane.setStyle("-fx-background-color: #000000; -fx-background-radius: 12;");
        videoPane.setPrefWidth(500);
        videoPane.setPrefHeight(320);
        plateImageView = new ImageView();
        plateImageView.setPreserveRatio(true);
        plateImageView.setFitWidth(500);
        plateImageView.setFitHeight(320);
        videoPane.getChildren().add(plateImageView);

        // Optional name input
        TextField nameField = new TextField();
        nameField.setPromptText("Full Name (optional)");
        nameField.setStyle("-fx-font-size: 20; -fx-background-radius: 16; -fx-border-radius: 16; -fx-border-color: #3bb3a6; -fx-background-color: #f5f5f5; -fx-padding: 8 12;");
        nameField.setPrefWidth(500);

        Label info = new Label("Center your face and tap Snap & Register.");
        info.setStyle("-fx-font-size: 16; -fx-text-fill: #555555;");

        Label result = new Label("");
        result.setStyle("-fx-font-size: 16; -fx-text-fill: #256862;");

        Button snapBtn = new Button("Snap & Register");
        snapBtn.setStyle("-fx-background-color: #3bb3a6; -fx-background-radius: 24; -fx-font-size: 22; -fx-text-fill: white; -fx-padding: 12 24;");
        snapBtn.setOnAction(e -> {
            Task<Void> snapTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    try {
                        // Get current frame bytes
                        HttpRequest frameReq = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:5001/plate-frame"))
                            .GET()
                            .build();
                        HttpResponse<byte[]> frameResp = httpClient.send(frameReq, HttpResponse.BodyHandlers.ofByteArray());
                        if (frameResp.statusCode() != 200 || frameResp.body() == null || frameResp.body().length == 0) {
                            Platform.runLater(() -> result.setText("No frame available. Try again."));
                            return null;
                        }
                        String b64 = Base64.getEncoder().encodeToString(frameResp.body());
                        String dataUrl = "data:image/jpeg;base64," + b64;
                        String name = nameField.getText() != null ? nameField.getText().trim() : "";
                        if (name.isEmpty()) {
                            name = (latestPlateText != null && !latestPlateText.isEmpty()) ? ("Plate " + latestPlateText.toUpperCase()) : "User";
                        }

                        JSONObject payload = new JSONObject();
                        payload.put("image", dataUrl);
                        payload.put("name", name);

                        HttpRequest regReq = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:5001/register-face"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                            .build();
                        HttpResponse<String> regResp = httpClient.send(regReq, HttpResponse.BodyHandlers.ofString());

                        if (regResp.statusCode() == 200) {
                            JSONObject res = new JSONObject(regResp.body());
                            if ("success".equals(res.optString("status"))) {
                                Platform.runLater(() -> result.setText("Registered! Account #" + res.optInt("account_number")));
                                // Stop plate scanning and continue to payment
                                stopPlateFlowAfterRegister();
                            } else {
                                Platform.runLater(() -> result.setText("Registration failed: " + res.optString("message")));
                            }
                        } else {
                            Platform.runLater(() -> result.setText("Registration failed (HTTP " + regResp.statusCode() + ")"));
                        }
                    } catch (Exception ex) {
                        Platform.runLater(() -> result.setText("Error: " + ex.getMessage()));
                    }
                    return null;
                }
            };
            new Thread(snapTask).start();
        });

        mainBox.getChildren().addAll(title, videoPane, nameField, info, snapBtn, result);
        root.getChildren().set(0, mainBox);
    }

    private void stopPlateFlowAfterRegister() {
        try {
            if (plateExecutor != null) {
                plateExecutor.shutdownNow();
                plateExecutor = null;
            }
        } catch (Exception ignored) {}
        isPlateScanning = false;

        try {
            HttpRequest stopPlate = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:5001/stop-plate-scanning"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
            httpClient.sendAsync(stopPlate, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {}

        // Keep face navigation suppressed during payment
        suppressFaceUiNavigation = true;
        // Navigate to payment info screen
        Platform.runLater(this::showPaymentInfoScreen);
    }

    private void resumeFacePolling() {
        try {
            faceCheckExecutor = Executors.newScheduledThreadPool(1);
            startPeriodicFaceCheck();
        } catch (Exception ignored) {}
    }
    
    private void stopFaceScanning() {
        Task<Void> stopScanTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:5001/stop-scanning"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println("Face scanning stopped: " + response.body());
                return null;
            }
        };
        
        new Thread(stopScanTask).start();
    }
} 