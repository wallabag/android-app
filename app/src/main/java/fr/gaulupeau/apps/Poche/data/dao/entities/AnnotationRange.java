package fr.gaulupeau.apps.Poche.data.dao.entities;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Index;
import org.greenrobot.greendao.query.QueryBuilder;

import java.util.Collection;

import fr.gaulupeau.apps.Poche.data.dao.AnnotationRangeDao;

@Entity
public class AnnotationRange {

    @Id
    private Long id;

    @Index
    private Long annotationId;

    private String start;
    private String end;
    private long startOffset;
    private long endOffset;

    @Generated(hash = 1558244025)
    public AnnotationRange() {}

    @Generated(hash = 398191827)
    public AnnotationRange(Long id, Long annotationId, String start, String end,
            long startOffset, long endOffset) {
        this.id = id;
        this.annotationId = annotationId;
        this.start = start;
        this.end = end;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAnnotationId() {
        return annotationId;
    }

    public void setAnnotationId(Long annotationId) {
        this.annotationId = annotationId;
    }

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getEnd() {
        return end;
    }

    public void setEnd(String end) {
        this.end = end;
    }

    public long getStartOffset() {
        return startOffset;
    }

    public void setStartOffset(long startOffset) {
        this.startOffset = startOffset;
    }

    public long getEndOffset() {
        return endOffset;
    }

    public void setEndOffset(long endOffset) {
        this.endOffset = endOffset;
    }

    @Override
    public String toString() {
        return "AnnotationRange{" +
                "id=" + id +
                ", annotationId=" + annotationId +
                ", start='" + start + '\'' +
                ", end='" + end + '\'' +
                ", startOffset=" + startOffset +
                ", endOffset=" + endOffset +
                '}';
    }

    public static QueryBuilder<AnnotationRange> getAnnotationRangesByAnnotationsQueryBuilder(
            Collection<Long> annotationIds, AnnotationRangeDao dao) {
        return dao.queryBuilder().where(AnnotationRangeDao.Properties.AnnotationId.in(annotationIds));
    }

}
