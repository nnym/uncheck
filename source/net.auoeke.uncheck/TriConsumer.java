package net.auoeke.uncheck;

@FunctionalInterface
interface TriConsumer<A, B, C> {
	void accept(A a, B b, C c);
}
