plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// Use modVersion from gradle.properties, falling back to 2.0.0 if not present.
version = project.findProperty("modVersion")?.toString() ?: "2.0.0"
