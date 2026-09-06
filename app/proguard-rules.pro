# The packing engine is plain Kotlin data classes and pure functions — nothing reflective,
# so no keep rules are needed for it. Add rules here as SDKs that do use reflection land
# (Room's generated code ships its own; ARCore and billing will need checking).
