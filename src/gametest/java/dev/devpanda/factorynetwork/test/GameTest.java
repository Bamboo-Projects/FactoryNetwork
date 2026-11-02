package dev.devpanda.factorynetwork.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a game test, in the shape the tests were written in up to 1.21.4.
 *
 * <p>Since 1.21.5 Minecraft registers tests as data instead of reading
 * annotations; {@link GameTests} turns every method carrying this one into
 * such a registration. The tests themselves stayed as they were.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GameTest {
    /** The structure under {@code data/factorynetwork/structure}. */
    String template();

    int timeoutTicks() default 100;

    int setupTicks() default 0;

    boolean required() default true;
}
