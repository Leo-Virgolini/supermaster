package ar.com.leo.super_master_backend.config;

import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.module.SimpleModule;

@Configuration
public class JacksonJsonNullableConfig {

    @Bean
    public JacksonModule jsonNullableModule() {
        SimpleModule module = new SimpleModule("json-nullable-module");
        module.addDeserializer(JsonNullable.class, new JsonNullableDeserializer());
        return module;
    }

    private static final class JsonNullableDeserializer extends StdDeserializer<JsonNullable<?>> {

        private final JavaType valueType;

        private JsonNullableDeserializer() {
            this(null);
        }

        private JsonNullableDeserializer(JavaType valueType) {
            super(JsonNullable.class);
            this.valueType = valueType;
        }

        @Override
        public JsonNullable<?> deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            JavaType targetType = valueType != null ? valueType : ctxt.constructType(Object.class);
            Object value = ctxt.readValue(p, targetType);
            return JsonNullable.of(value);
        }

        @Override
        public JsonNullable<?> getNullValue(DeserializationContext ctxt) {
            return JsonNullable.of(null);
        }

        @Override
        public JsonNullable<?> deserializeWithType(JsonParser p, DeserializationContext ctxt, tools.jackson.databind.jsontype.TypeDeserializer typeDeserializer) throws JacksonException {
            return deserialize(p, ctxt);
        }

        @Override
        public tools.jackson.databind.ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
            JavaType contextual = property != null ? property.getType() : ctxt.getContextualType();
            JavaType contained = (contextual != null && contextual.containedTypeCount() > 0)
                    ? contextual.containedType(0)
                    : ctxt.constructType(Object.class);
            return new JsonNullableDeserializer(contained);
        }
    }
}
