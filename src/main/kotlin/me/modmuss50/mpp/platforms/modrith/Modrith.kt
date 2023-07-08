package me.modmuss50.mpp.platforms.modrith

import me.modmuss50.mpp.Platform
import me.modmuss50.mpp.PlatformDependency
import me.modmuss50.mpp.PlatformDependencyContainer
import me.modmuss50.mpp.PlatformOptions
import me.modmuss50.mpp.path
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkQueue
import java.nio.file.Path
import javax.inject.Inject
import kotlin.reflect.KClass

interface ModrithOptions : PlatformOptions, PlatformDependencyContainer<ModrithDependency> {
    @get:Input
    val projectId: Property<String>

    @get:Input
    val minecraftVersions: ListProperty<String>

    @get:Input
    val featured: Property<Boolean>

    @get:Input
    val apiEndpoint: Property<String>

    fun from(other: ModrithOptions) {
        super.from(other)
        fromDependencies(other)
        projectId.set(other.projectId)
        minecraftVersions.set(other.minecraftVersions)
        featured.set(other.featured)
        apiEndpoint.set(other.apiEndpoint)
    }

    override val platformDependencyKClass: KClass<ModrithDependency>
        get() = ModrithDependency::class
}

interface ModrithDependency : PlatformDependency {
    @get:Input
    val projectId: Property<String>

    @get:Input
    @get:Optional
    val versionId: Property<String>
}

abstract class Modrith @Inject constructor(name: String) : Platform(name), ModrithOptions {
    init {
        featured.convention(false)
        apiEndpoint.convention("https://api.modrinth.com")
    }

    override fun publish(queue: WorkQueue) {
        queue.submit(UploadWorkAction::class.java) {
            it.from(this)
        }
    }

    interface UploadParams : WorkParameters, ModrithOptions

    abstract class UploadWorkAction : WorkAction<UploadParams> {
        override fun execute() {
            with(parameters) {
                val api = ModrithApi(accessToken.get(), apiEndpoint.get())

                val primaryFileKey = "primaryFile"
                val files = HashMap<String, Path>()
                files[primaryFileKey] = file.path

                additionalFiles.files.forEachIndexed { index, additionalFile ->
                    files["file_$index"] = additionalFile.toPath()
                }

                val dependencies = dependencies.get().map {
                    ModrithApi.Dependency(
                        projectId = it.projectId.get(),
                        versionId = it.versionId.orNull,
                        dependencyType = ModrithApi.DependencyType.valueOf(it.type.get()),
                    )
                }

                val metadata = ModrithApi.CreateVersion(
                    name = displayName.getOrElse(file.get().asFile.name),
                    versionNumber = version.get(),
                    changelog = changelog.orNull,
                    dependencies = dependencies,
                    gameVersions = minecraftVersions.get(),
                    versionType = ModrithApi.VersionType.valueOf(type.get()),
                    loaders = modLoaders.get().map { it.lowercase() },
                    featured = featured.get(),
                    projectId = projectId.get(),
                    fileParts = files.keys.toList(),
                    primaryFile = primaryFileKey,
                )

                // TODO get response
                api.createVersion(metadata, files)
            }
        }
    }
}
