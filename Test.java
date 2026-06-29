import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import java.lang.reflect.Method;
public class Test {
    public static void main(String[] args) {
        for (Method m : IDiscoveryPipeline.class.getMethods()) {
            if (m.getName().equals("addJarContent")) {
                System.out.println("addJarContent params:");
                for (Class<?> p : m.getParameterTypes()) {
                    System.out.println(" - " + p.getName());
                }
            }
        }
    }
}
