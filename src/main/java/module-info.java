module kamaletdinov.dev.orthotesting {
    requires javafx.controls;
    requires javafx.fxml;


    opens kamaletdinov.dev.orthotesting to javafx.fxml;
    exports kamaletdinov.dev.orthotesting;
}