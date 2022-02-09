package net.auoeke.uncheck;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

public interface Transformer extends ClassFileTransformer {
    @Override byte[] transform(Module module, ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytes) throws IllegalClassFormatException;

    @Override default byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytes) throws IllegalClassFormatException {
        return this.transform(null, loader, name, type, domain, bytes);
    }
}
