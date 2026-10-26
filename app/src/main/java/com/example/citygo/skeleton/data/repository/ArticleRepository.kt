package com.example.citygo.skeleton.data.repository

import com.example.citygo.skeleton.data.model.ArticleComment
import com.example.citygo.skeleton.data.model.PublishArticleRequest
import com.example.citygo.skeleton.data.model.TravelArticle

/* =====================================================================================
 * 游记社区仓库
 *
 * UI（社区首页 / 详情页 / 发布页）→ ShareViewModel → 本接口。
 * 当前装配 [MockArticleRepository]：内存列表 + 模拟网络延迟 + 分页；
 *
 * 【后端替换位置】新增 RetrofitArticleRepository 实现本接口，在 RepositoryContainer
 * 替换装配即可，ViewModel / UI 零改动。预计对应接口：
 * - GET  /articles?page=&size=          分页游记列表
 * - GET  /articles/{articleId}          游记详情
 * - POST /articles                      发布游记（图片先上传换 URL）
 * - POST /articles/{id}/like            点赞 / 取消点赞
 * - POST /articles/{id}/collect         收藏 / 取消收藏
 * - GET  /articles/{id}/comments        评论列表
 * - POST /articles/{id}/comments        发表评论
 * ===================================================================================== */

interface ArticleRepository {

    /** 分页拉取游记列表（页码从 1 开始） */
    suspend fun getArticles(page: Int, pageSize: Int): List<TravelArticle>

    /** 按 id 获取游记；不存在返回 null */
    suspend fun getArticleById(articleId: String): TravelArticle?

    /** 发布游记，返回入库后的完整实体（插入到列表最前面） */
    suspend fun publishArticle(request: PublishArticleRequest): TravelArticle

    /** 切换点赞状态，返回更新后的游记 */
    suspend fun toggleLike(articleId: String): TravelArticle?

    /** 切换收藏状态，返回更新后的游记 */
    suspend fun toggleCollect(articleId: String): TravelArticle?

    /** 新增评论，返回更新后的游记 */
    suspend fun addComment(articleId: String, comment: ArticleComment): TravelArticle?
}

/**
 * 游记社区 Mock 实现：
 * - 内置 30 篇游记（场景 / 标题模板循环生成），每页 6 篇模拟上拉加载；
 * - 用户新发布的游记插入内存列表最前；
 * - 点赞 / 收藏 / 评论直接改内存数据，重启 App 后还原。
 */
class MockArticleRepository : ArticleRepository {

    private val lock = Any()

    /** 内存数据源（用户发布 / 点赞 / 评论都改这里） */
    private val articles: MutableList<TravelArticle> =
        generateMockArticles().toMutableList()

    override suspend fun getArticles(page: Int, pageSize: Int): List<TravelArticle> {
        // —— 模拟网络请求延迟（真实实现删除此处，直接调用 Retrofit 接口） ——
        kotlinx.coroutines.delay(if (page == 1) 500L else 700L)
        synchronized(lock) {
            val from = ((page - 1) * pageSize).coAtMost(articles.size)
            val to = (from + pageSize).coerceAtMost(articles.size)
            return if (from >= to) emptyList() else articles.subList(from, to).toList()
        }
    }

    override suspend fun getArticleById(articleId: String): TravelArticle? {
        kotlinx.coroutines.delay(120L)
        return synchronized(lock) { articles.firstOrNull { it.articleId == articleId } }
    }

    override suspend fun publishArticle(request: PublishArticleRequest): TravelArticle {
        kotlinx.coroutines.delay(400L)
        val article = TravelArticle(
            articleId = "u" + System.currentTimeMillis(),
            authorName = request.authorName,
            title = request.title,
            previewText = request.content.take(40),
            imgUrls = request.imgUrls,
            content = request.content,
            likeCount = 0,
            collectCount = 0,
            commentList = emptyList(),
        )
        synchronized(lock) { articles.add(0, article) }
        return article
    }

    override suspend fun toggleLike(articleId: String): TravelArticle? = update(articleId) { a ->
        a.copy(
            isLiked = !a.isLiked,
            likeCount = a.likeCount + if (a.isLiked) -1 else 1,
        )
    }

    override suspend fun toggleCollect(articleId: String): TravelArticle? = update(articleId) { a ->
        a.copy(
            isCollected = !a.isCollected,
            collectCount = a.collectCount + if (a.isCollected) -1 else 1,
        )
    }

    override suspend fun addComment(articleId: String, comment: ArticleComment): TravelArticle? =
        update(articleId) { a -> a.copy(commentList = a.commentList + comment) }

    private inline fun update(id: String, transform: (TravelArticle) -> TravelArticle): TravelArticle? =
        synchronized(lock) {
            val index = articles.indexOfFirst { it.articleId == id }
            if (index < 0) return@synchronized null
            articles[index] = transform(articles[index])
            articles[index]
        }

    private fun Int.coAtMost(that: Int): Int = if (this > that) that else this

    /* ------------------------------- Mock 数据生成 ------------------------------- */

    private fun generateMockArticles(): List<TravelArticle> {
        val result = ArrayList<TravelArticle>(TOTAL_MOCK)
        repeat(TOTAL_MOCK) { index ->
            val meta = MOCK_METAS[index % MOCK_METAS.size]
            // 每个模板在不同页循环时换一个场景 id，保证封面不重复
            val sceneOffset = index / MOCK_METAS.size
            val cover = "mock://scene/${(meta.sceneId + sceneOffset) % SCENE_COUNT}"
            val second = "mock://scene/${(meta.sceneId + 3 + sceneOffset) % SCENE_COUNT}"
            val third = "mock://scene/${(meta.sceneId + 6 + sceneOffset) % SCENE_COUNT}"
            result += TravelArticle(
                articleId = (1001 + index).toString(),
                authorName = meta.author,
                title = meta.title,
                previewText = meta.preview,
                imgUrls = listOf(cover, second, third),
                content = meta.content,
                likeCount = meta.likes + index * 7,
                collectCount = meta.collects + index * 3,
                isLiked = false,
                isCollected = false,
                commentList = meta.comments,
            )
        }
        return result
    }

    private companion object {
        /** Mock 游记总数（配合每页 6 条可翻 5 页） */
        const val TOTAL_MOCK = 30
        const val SCENE_COUNT = 12

        /** 标题 / 作者 / 正文模板（循环生成） */
        val MOCK_METAS = listOf(
            MockMeta(
                sceneId = 0,
                author = "旅行者小A",
                title = "长沙3天小众探店，避开人潮",
                preview = "挖到很多本地人私藏小店，附地图点位…",
                content = "第一天从五一广场背后的老巷开始，笨萝卜要赶在 11 点前到。\n\n" +
                    "第二天留给岳麓山脚下的咖啡馆，下午光线穿过梧桐叶特别出片。\n\n" +
                    "第三天去四方坪夜市，紫苏桃子姜和口味虾一定要试！全程地铁 + 步行，人均不到六百。",
                likes = 235,
                collects = 89,
                comments = listOf(
                    ArticleComment("用户B", "这个店我也去过！"),
                    ArticleComment("用户C", "收藏了，准备去打卡"),
                ),
            ),
            MockMeta(
                sceneId = 1,
                author = "风里有诗",
                title = "大理环洱海骑行，风都是自由的",
                preview = "才村出发，S弯、喜洲、双廊一路拍…",
                content = "租了一辆带儿童座椅的电驴，沿着生态廊道慢慢骑。\n\n" +
                    "S 弯上午人少，喜洲的稻田在九月还是绿的，双廊的日落值得原地等一小时。\n" +
                    "全程 120 公里分两天，晚上住喜洲古镇，吃现烤的喜洲粑粑。",
                likes = 510,
                collects = 203,
                comments = listOf(
                    ArticleComment("阿柠", "请问电驴在哪里租的呀？"),
                    ArticleComment("风里有诗", "才村码头门口很多店，记得砍价～"),
                ),
            ),
            MockMeta(
                sceneId = 2,
                author = "快门按不停",
                title = "重庆夜景机位合集｜赛博山城",
                preview = "洪崖洞、千厮门大桥、南山一棵树…",
                content = "千厮门大桥上人最多，其实桥底嘉陵江边的礁石才是最佳全景位。\n\n" +
                    "南山一棵树建议提前一小时占位；如果懒得爬山，龙门浩老街的观景平台视野也很能打。\n" +
                    "夜景记得带三脚架，手机长曝光也能拍出赛博感。",
                likes = 367,
                collects = 156,
                comments = listOf(
                    ArticleComment("山城土著", "礁石位汛期不要去，安全第一！"),
                ),
            ),
            MockMeta(
                sceneId = 3,
                author = "金毛是只狗",
                title = "三亚带狗看海｜宠物友好沙滩",
                preview = "毛孩子也能踩沙滩！4 个可牵绳海湾…",
                content = "整理了四个可以牵绳进入的海湾，最推荐海棠湾最北端，人少沙细。\n\n" +
                    "中午太阳毒，建议早八晚五遛狗；宠物酒店选带小院的，狗子可以自己疯跑。\n" +
                    "回程前记得检查犬证和疫苗本，过海检查会看。",
                likes = 189,
                collects = 98,
                comments = listOf(
                    ArticleComment("柯基麻麻", "下周就带崽去！"),
                ),
            ),
            MockMeta(
                sceneId = 4,
                author = "吃货阿团",
                title = "成都三日 Citywalk｜人均 800 吃到扶墙",
                preview = "春熙路、宽窄巷子、建设路全攻略…",
                content = "建设路小吃街建议两个人分一份，不然走到一半就饱了。\n\n" +
                    "宽窄巷子旁边的泡桐树街才是本地人喝茶的地方，盖碗茶十五块坐一下午。\n" +
                    "晚上去九眼桥边散步，第二天都江堰半日游，高铁半小时直达。",
                likes = 238,
                collects = 142,
                comments = listOf(
                    ArticleComment("不吃香菜", "蛋烘糕必须有姓名！"),
                    ArticleComment("熊猫团子", "收藏这一篇就够了"),
                ),
            ),
            MockMeta(
                sceneId = 5,
                author = "盛唐小公主",
                title = "西安汉服一日体验｜大唐不夜城",
                preview = "妆造199全包，十二时辰街区出片…",
                content = "汉服店选在大雁塔附近，妆造加衣服 199 可以用一整天。\n\n" +
                    "大唐不夜城晚上八点后灯全开，跟拍建议提前三天约。\n" +
                    "第二天顺路去陕博，记得提前一周抢票，何家村金器值得慢慢看。",
                likes = 421,
                collects = 276,
                comments = listOf(
                    ArticleComment("小米", "冬天穿汉服会不会很冷"),
                    ArticleComment("盛唐小公主", "店里有暖宝宝，裙摆下面塞秋裤没人看得到哈哈"),
                ),
            ),
            MockMeta(
                sceneId = 6,
                author = "晨跑的鱼",
                title = "杭州西湖晨跑路线｜六点的苏堤",
                preview = "没有游客只有鸟鸣，5公里湖景跑道…",
                content = "六点从曲院风荷进苏堤，整座桥都是你的。\n\n" +
                    "跑完在北山街吃个葱包烩，骑共享单车沿北山街到宝石山，二十分钟登顶看西湖全景。\n" +
                    "这条线适合所有体力，慢慢走也行。",
                likes = 98,
                collects = 54,
                comments = emptyList(),
            ),
            MockMeta(
                sceneId = 7,
                author = "岛屿来信",
                title = "厦门鼓浪屿慢生活三日",
                preview = "老别墅民宿、猫街、海边咖啡…",
                content = "船票一定提前在官方小程序买，提前四十分钟到码头。\n\n" +
                    "岛上住一晚，等一日游的人潮退去，龙头路都安静下来。\n" +
                    "第三天回厦门本岛，沙坡尾的黄昏和八市的海鲜不要错过。",
                likes = 154,
                collects = 88,
                comments = listOf(
                    ArticleComment("海带", "民宿有推荐吗！"),
                ),
            ),
        )
    }

    /** Mock 模板结构 */
    private data class MockMeta(
        val sceneId: Int,
        val author: String,
        val title: String,
        val preview: String,
        val content: String,
        val likes: Int,
        val collects: Int,
        val comments: List<ArticleComment>,
    )
}
