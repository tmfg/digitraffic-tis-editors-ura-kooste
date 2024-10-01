package fi.digitraffic.ura.kooste;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;


@Path("/hello")
public class HelloResource {

    private final HelloService service;

    public HelloResource(HelloService service) {
        this.service = service;
    }

    @CheckedTemplate
    public static class Templates {

        public static native TemplateInstance greeting(String nav, String name);

    }

    @GET
    @Path("/greeting")
    public TemplateInstance greeting() {
        return doGreeting(null);
    }

    @GET
    @Path("/greeting/{name}")
    public TemplateInstance greeting(String name) {
        return doGreeting(name);
    }

    private TemplateInstance doGreeting(String name) {
        if (name == null || name.isEmpty()) {
            name = "unnamed wanderer";
        }
        return Templates.greeting("nav TBD", service.greeting(name));
    }

}
