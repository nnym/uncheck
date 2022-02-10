package net.auoeke.uncheck.intellij;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public interface Transformer extends ClassFileTransformer {
    @Override byte[] transform(Module module, ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytes);

    @Override default byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytes) {
        return this.transform(null, loader, name, type, domain, bytes);
    }
}
