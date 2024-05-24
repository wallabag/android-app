package fr.gaulupeau.apps.Poche.data.dao;

import java.util.Map;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.AbstractDaoSession;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.identityscope.IdentityScopeType;
import org.greenrobot.greendao.internal.DaoConfig;

import fr.gaulupeau.apps.Poche.data.dao.entities.Annotation;
import fr.gaulupeau.apps.Poche.data.dao.entities.AnnotationRange;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.data.dao.entities.ArticleContent;
import fr.gaulupeau.apps.Poche.data.dao.entities.ArticleTagsJoin;
import fr.gaulupeau.apps.Poche.data.dao.entities.QueueItem;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;

import fr.gaulupeau.apps.Poche.data.dao.AnnotationDao;
import fr.gaulupeau.apps.Poche.data.dao.AnnotationRangeDao;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.ArticleContentDao;
import fr.gaulupeau.apps.Poche.data.dao.ArticleTagsJoinDao;
import fr.gaulupeau.apps.Poche.data.dao.QueueItemDao;
import fr.gaulupeau.apps.Poche.data.dao.TagDao;

// This code was generated, edit with caution

/**
 * {@inheritDoc}
 * 
 * @see org.greenrobot.greendao.AbstractDaoSession
 */
public class DaoSession extends AbstractDaoSession {

    private final DaoConfig annotationDaoConfig;
    private final DaoConfig annotationRangeDaoConfig;
    private final DaoConfig articleDaoConfig;
    private final DaoConfig articleContentDaoConfig;
    private final DaoConfig articleTagsJoinDaoConfig;
    private final DaoConfig queueItemDaoConfig;
    private final DaoConfig tagDaoConfig;

    private final AnnotationDao annotationDao;
    private final AnnotationRangeDao annotationRangeDao;
    private final ArticleDao articleDao;
    private final ArticleContentDao articleContentDao;
    private final ArticleTagsJoinDao articleTagsJoinDao;
    private final QueueItemDao queueItemDao;
    private final TagDao tagDao;

    public DaoSession(Database db, IdentityScopeType type, Map<Class<? extends AbstractDao<?, ?>>, DaoConfig>
            daoConfigMap) {
        super(db);

        annotationDaoConfig = daoConfigMap.get(AnnotationDao.class).clone();
        annotationDaoConfig.initIdentityScope(type);

        annotationRangeDaoConfig = daoConfigMap.get(AnnotationRangeDao.class).clone();
        annotationRangeDaoConfig.initIdentityScope(type);

        articleDaoConfig = daoConfigMap.get(ArticleDao.class).clone();
        articleDaoConfig.initIdentityScope(type);

        articleContentDaoConfig = daoConfigMap.get(ArticleContentDao.class).clone();
        articleContentDaoConfig.initIdentityScope(type);

        articleTagsJoinDaoConfig = daoConfigMap.get(ArticleTagsJoinDao.class).clone();
        articleTagsJoinDaoConfig.initIdentityScope(type);

        queueItemDaoConfig = daoConfigMap.get(QueueItemDao.class).clone();
        queueItemDaoConfig.initIdentityScope(type);

        tagDaoConfig = daoConfigMap.get(TagDao.class).clone();
        tagDaoConfig.initIdentityScope(type);

        annotationDao = new AnnotationDao(annotationDaoConfig, this);
        annotationRangeDao = new AnnotationRangeDao(annotationRangeDaoConfig, this);
        articleDao = new ArticleDao(articleDaoConfig, this);
        articleContentDao = new ArticleContentDao(articleContentDaoConfig, this);
        articleTagsJoinDao = new ArticleTagsJoinDao(articleTagsJoinDaoConfig, this);
        queueItemDao = new QueueItemDao(queueItemDaoConfig, this);
        tagDao = new TagDao(tagDaoConfig, this);

        registerDao(Annotation.class, annotationDao);
        registerDao(AnnotationRange.class, annotationRangeDao);
        registerDao(Article.class, articleDao);
        registerDao(ArticleContent.class, articleContentDao);
        registerDao(ArticleTagsJoin.class, articleTagsJoinDao);
        registerDao(QueueItem.class, queueItemDao);
        registerDao(Tag.class, tagDao);
    }
    
    public void clear() {
        annotationDaoConfig.clearIdentityScope();
        annotationRangeDaoConfig.clearIdentityScope();
        articleDaoConfig.clearIdentityScope();
        articleContentDaoConfig.clearIdentityScope();
        articleTagsJoinDaoConfig.clearIdentityScope();
        queueItemDaoConfig.clearIdentityScope();
        tagDaoConfig.clearIdentityScope();
    }

    public AnnotationDao getAnnotationDao() {
        return annotationDao;
    }

    public AnnotationRangeDao getAnnotationRangeDao() {
        return annotationRangeDao;
    }

    public ArticleDao getArticleDao() {
        return articleDao;
    }

    public ArticleContentDao getArticleContentDao() {
        return articleContentDao;
    }

    public ArticleTagsJoinDao getArticleTagsJoinDao() {
        return articleTagsJoinDao;
    }

    public QueueItemDao getQueueItemDao() {
        return queueItemDao;
    }

    public TagDao getTagDao() {
        return tagDao;
    }

}
