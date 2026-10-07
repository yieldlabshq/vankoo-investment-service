package com.liquilabs.vankoo.investment.infrastructure.messaging.outbox;

import com.liquilabs.vankoo.investment.VankooInvestmentServiceApplication;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El productor del ciclo de vida tiene que serializar la clave como texto.
 *
 * {@link OutboxPublisher} pone el {@code aggregateId} —un String— en
 * {@code KafkaHeaders.KEY}, mientras que el payload viaja como {@code byte[]}. Sin
 * esta configuración el binder elige {@code ByteArraySerializer} para las dos mitades
 * del mensaje y cada envío muere con un {@code ClassCastException}: la proyección del
 * Marketplace nunca recibe nada y la búsqueda queda vacía para siempre, aunque la
 * subasta esté publicada en la base de datos.
 *
 * Se lee el YAML en lugar de levantar el contexto porque el fallo era de configuración
 * y no de código: {@code OutboxPublisherTest} ya comprueba que la clave sale como
 * String, y lo que faltaba era que el binding estuviera de acuerdo. Tampoco lo habría
 * pillado un test sobre el binder de prueba, que guarda los mensajes en memoria sin
 * serializarlos.
 */
class AuctionLifecycleProducerConfigurationTest {

    private static final String BINDING_PREFIX =
            "spring.cloud.stream.kafka.bindings.auctionLifecycle-out-0.producer";

    @Test
    void declaresTheStringKeySerializerTheOutboxPublisherNeeds() throws Exception {
        List<PropertySource<?>> configuration = shippedConfiguration();

        // Guardia: `src/test/resources/application.yaml` tapa al de producción en el
        // classpath de pruebas, y no declara este binding. Sin esta comprobación el
        // test pasaría leyendo el fichero equivocado y no vigilaría nada.
        assertThat(property(configuration, BINDING_PREFIX + ".sync"))
                .as("el application.yaml de producción, no el de pruebas")
                .isEqualTo(true);

        assertThat(property(configuration, BINDING_PREFIX + ".configuration.key.serializer"))
                .isEqualTo(StringSerializer.class.getName());
    }

    /**
     * El `application.yaml` tal y como se empaqueta, junto a la clase de arranque.
     *
     * No vale leer `src/main/resources`: ahí las propiedades de Maven todavía son
     * marcadores (`@project.name@`) y el parser de YAML se atraganta con la arroba.
     */
    private static List<PropertySource<?>> shippedConfiguration() throws Exception {
        Resource resource = new FileSystemResource(classesDirectory().resolve("application.yaml"));
        return new YamlPropertySourceLoader().load("application", resource);
    }

    private static Path classesDirectory() throws URISyntaxException {
        return Path.of(VankooInvestmentServiceApplication.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    }

    private static Object property(List<PropertySource<?>> sources, String name) {
        return sources.stream()
                .map(source -> source.getProperty(name))
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }
}
