package app.revanced.manager.data.room.options

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.revanced.manager.data.room.bundles.PatchBundleEntity

@Entity(
    tableName = "option_groups",
    foreignKeys = [ForeignKey(
        PatchBundleEntity::class,
        parentColumns = ["uid"],
        childColumns = ["patch_bundle"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["patch_bundle", "package_name"], unique = true)]
)
data class OptionGroup(
    @PrimaryKey val uid: Int,
    @ColumnInfo(name = "patch_bundle") val patchBundle: Int,
    @ColumnInfo(name = "package_name") val packageName: String
)