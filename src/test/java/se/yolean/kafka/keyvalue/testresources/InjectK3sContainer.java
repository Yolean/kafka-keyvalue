package se.yolean.kafka.keyvalue.testresources;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;

@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface InjectK3sContainer {
}
