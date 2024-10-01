package fi.digitraffic.ura.kooste;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class HelloService {

    public String greeting(String name) {
        return "hello " + name;
    }

}
