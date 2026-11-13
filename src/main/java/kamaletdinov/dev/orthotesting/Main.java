package kamaletdinov.dev.orthotesting;

public class Main {
    public static void main(String[] args) {
        // Мы запускаем метод main основного класса через класс,
        // который НЕ наследуется от JavaFX Application.
        HelloApplication.main(args);
    }
}
