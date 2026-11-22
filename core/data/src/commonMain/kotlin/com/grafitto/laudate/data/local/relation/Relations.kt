package com.grafitto.laudate.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.grafitto.laudate.data.local.entity.ArrangementEntity
import com.grafitto.laudate.data.local.entity.CelebrationEntity
import com.grafitto.laudate.data.local.entity.CelebrationItemEntity
import com.grafitto.laudate.data.local.entity.ChordAnchorEntity
import com.grafitto.laudate.data.local.entity.SongEntity
import com.grafitto.laudate.data.local.entity.SongSectionEntity
import com.grafitto.laudate.data.local.entity.UserAnnotationEntity

data class SectionWithAnchorsRelation(
    @Embedded val section: SongSectionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "section_id"
    )
    val anchors: List<ChordAnchorEntity>
)

data class SongWithDetailsRelation(
    @Embedded val song: SongEntity,
    @Relation(
        entity = SongSectionEntity::class,
        parentColumn = "id",
        entityColumn = "song_id"
    )
    val sections: List<SectionWithAnchorsRelation>,
    @Relation(
        parentColumn = "id",
        entityColumn = "song_id"
    )
    val arrangements: List<ArrangementEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "song_id"
    )
    val annotations: List<UserAnnotationEntity>
)

data class CelebrationItemWithSongRelation(
    @Embedded val item: CelebrationItemEntity,
    @Relation(
        parentColumn = "song_id",
        entityColumn = "id"
    )
    val song: SongEntity
)

data class CelebrationWithItemsRelation(
    @Embedded val celebration: CelebrationEntity,
    @Relation(
        entity = CelebrationItemEntity::class,
        parentColumn = "id",
        entityColumn = "celebration_id"
    )
    val items: List<CelebrationItemWithSongRelation>
)
