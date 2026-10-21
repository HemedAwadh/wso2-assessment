package org.example.customerservicespringboot.models;

import java.util.List;

public final class MockUserData {

    // Prevent instantiation
    private MockUserData() {}

    public static final List<User> USERS = List.of(
            new User(
                    1, "Leanne Graham", "Bret", "Sincere@april.biz",
                    new Address("Kulas Light", "Apt. 556", "Gwenborough", "92998-3874", new Geo("-37.3159", "81.1496")),
                    "1-770-736-8031 x56442", "hildegard.org",
                    new Company("Romaguera-Crona", "Multi-layered client-server neural-net", "harness real-time e-markets")
            ),
            new User(
                    2, "Ervin Howell", "Antonette", "Shanna@melissa.tv",
                    new Address("Victor Plains", "Suite 879", "Wisokyburgh", "90566-7771", new Geo("-43.9509", "-34.4618")),
                    "010-692-6593 x09125", "anastasia.net",
                    new Company("Deckow-Crist", "Proactive didactic contingency", "synergize scalable supply-chains")
            ),
            new User(
                    3, "Clementine Bauch", "Samantha", "Nathan@yesenia.net",
                    new Address("Douglas Extension", "Suite 847", "McKenziehaven", "59590-4157", new Geo("-68.6102", "-47.0653")),
                    "1-463-123-4447", "ramiro.info",
                    new Company("Romaguera-Jacobson", "Face to face bifurcated interface", "e-enable strategic applications")
            ),
            new User(
                    4, "Patricia Lebsack", "Karianne", "Julianne.OConner@kory.org",
                    new Address("Hoeger Mall", "Apt. 692", "South Elvis", "53919-4257", new Geo("29.4572", "-164.2990")),
                    "493-170-9623 x156", "kale.biz",
                    new Company("Robel-Corkery", "Multi-tiered zero tolerance productivity", "transition cutting-edge web services")
            )
    );
}