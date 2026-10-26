package com.example.citygo.skeleton.data.model

/* =====================================================================================
 * 城市名拼音索引（无音调）
 *
 * 目的：天气城市搜索除汉字直接匹配外，额外支持英文键盘输入拼音：
 * - 全拼：suzhou（苏州）、changsha（长沙）、eerduosi（鄂尔多斯）
 * - 首字母：sz、cs、xj（新疆下的城市按省份首字母可命中）
 *
 * 说明：
 * - 仅收录全国城市目录中出现过的汉字；新增城市后若有未收录汉字，
 *   拼音/首字母匹配会自动降级为该字原样（CityPinyinCoverageTest 单测会兜底检查覆盖率）。
 * - 多音字给出多个读音候选（笛卡尔积展开），尽量覆盖用户的实际输入习惯，
 *   例如 长沙(Changsha) 的「长」同时保留 chang / zhang，
 *   百色保留 bai / bo，喀什保留 shen / shi。
 * - ü 统一用 v 表示（吕梁 = lvliang）。
 * ===================================================================================== */

/** 汉字 → 读音候选原始表（允许重复出现，构造时按字取并集） */
private val PINYIN_RAW: List<Pair<Char, List<String>>> = listOf(
    // —— 数字 / 常用泛指字 ——
    '七' to listOf("qi"), '三' to listOf("san"), '上' to listOf("shang"),
    '中' to listOf("zhong"), '东' to listOf("dong"), '西' to listOf("xi"),
    '南' to listOf("nan"), '北' to listOf("bei"), '口' to listOf("kou"),
    '台' to listOf("tai"), '吉' to listOf("ji"), '同' to listOf("tong"),
    '名' to listOf("ming"), '和' to listOf("he"), '哈' to listOf("ha"),
    '大' to listOf("da"), '天' to listOf("tian"), '太' to listOf("tai"),
    '头' to listOf("tou"), '宁' to listOf("ning"), '安' to listOf("an"),
    '定' to listOf("ding"), '家' to listOf("jia"), '山' to listOf("shan"),
    '州' to listOf("zhou"), '市' to listOf("shi"), '平' to listOf("ping"),
    '广' to listOf("guang"), '庆' to listOf("qing"), '建' to listOf("jian"),
    '开' to listOf("kai"), '德' to listOf("de"), '心' to listOf("xin"),
    '成' to listOf("cheng"), '承' to listOf("cheng"), '新' to listOf("xin"),
    '昌' to listOf("chang"), '明' to listOf("ming"), '春' to listOf("chun"),
    '普' to listOf("pu"), '景' to listOf("jing"), '曲' to listOf("qu"),
    '有' to listOf("you"), '木' to listOf("mu"), '本' to listOf("ben"),
    '来' to listOf("lai"), '杨' to listOf("yang"), '杰' to listOf("jie"),
    '林' to listOf("lin"), '果' to listOf("guo"), '根' to listOf("gen"),
    '桥' to listOf("qiao"), '梧' to listOf("wu"), '楚' to listOf("chu"),
    '武' to listOf("wu"), '毕' to listOf("bi"), '水' to listOf("shui"),
    '江' to listOf("jiang"), '池' to listOf("chi"), '汤' to listOf("tang"),
    '沟' to listOf("gou"), '河' to listOf("he"), '泉' to listOf("quan"),
    '法' to listOf("fa"), '泊' to listOf("bo", "po"), '沿' to listOf("yan"),
    '泰' to listOf("tai"), '泽' to listOf("ze"), '洋' to listOf("yang"),
    '洗' to listOf("xi"), '洞' to listOf("dong"), '洪' to listOf("hong"),
    '洛' to listOf("luo"), '津' to listOf("jin"), '洱' to listOf("er"),
    '洲' to listOf("zhou"), '济' to listOf("ji"), '浪' to listOf("lang"),
    '海' to listOf("hai"), '涂' to listOf("tu"), '涌' to listOf("yong"),
    '源' to listOf("yuan"), '湾' to listOf("wan"), '满' to listOf("man"),
    '滨' to listOf("bin"), '滩' to listOf("tan"), '潭' to listOf("tan"),
    '潮' to listOf("chao"), '澜' to listOf("lan"), '灵' to listOf("ling"),
    '烟' to listOf("yan"), '热' to listOf("re"), '熟' to listOf("shu"),
    '尔' to listOf("er"), '片' to listOf("pian"), '特' to listOf("te"),
    '玉' to listOf("yu"), '王' to listOf("wang"), '珠' to listOf("zhu"),
    '玛' to listOf("ma"), '班' to listOf("ban"), '现' to listOf("xian"),
    '理' to listOf("li"), '琼' to listOf("qiong"), '甘' to listOf("gan"),
    '甜' to listOf("tian"), '生' to listOf("sheng"), '用' to listOf("yong"),
    '番' to listOf("fan"), '疏' to listOf("shu"), '白' to listOf("bai"),
    '盘' to listOf("pan"), '盛' to listOf("sheng"), '直' to listOf("zhi"),
    '相' to listOf("xiang"), '省' to listOf("sheng"), '真' to listOf("zhen"),
    '眼' to listOf("yan"), '石' to listOf("shi"), '破' to listOf("po"),
    '硕' to listOf("shuo"), '博' to listOf("bo"), '县' to listOf("xian"),
    '古' to listOf("gu"), '华' to listOf("hua"), '协' to listOf("xie"),
    '南' to listOf("nan"), '博' to listOf("bo"),

    // —— 行政区划通用字 ——
    '自' to listOf("zi"), '治' to listOf("zhi"), '区' to listOf("qu"),
    '壮' to listOf("zhuang"), '族' to listOf("zu"), '回' to listOf("hui"),
    '维' to listOf("wei"), '吾' to listOf("wu"), '蒙' to listOf("meng"),
    '内' to listOf("nei"), '藏' to listOf("zang", "cang"), '徽' to listOf("hui"),
    '陕' to listOf("shan"), '肃' to listOf("su"), '疆' to listOf("jiang"),

    // —— 城市目录汉字 ——
    '京' to listOf("jing"), '津' to listOf("jin"), '重' to listOf("chong", "zhong"),
    '庆' to listOf("qing"),
    '石' to listOf("shi"), '家' to listOf("jia"), '庄' to listOf("zhuang"),
    '唐' to listOf("tang"), '秦' to listOf("qin"), '皇' to listOf("huang"),
    '岛' to listOf("dao"), '邯' to listOf("han"), '郸' to listOf("dan"),
    '邢' to listOf("xing"), '保' to listOf("bao"), '张' to listOf("zhang"),
    '承' to listOf("cheng"), '德' to listOf("de"), '沧' to listOf("cang"),
    '廊' to listOf("lang"), '坊' to listOf("fang"), '衡' to listOf("heng"),
    '水' to listOf("shui"),
    '原' to listOf("yuan"), '阳' to listOf("yang"), '泉' to listOf("quan"),
    '长' to listOf("chang", "zhang"), '治' to listOf("zhi"), '晋' to listOf("jin"),
    '城' to listOf("cheng"), '朔' to listOf("shuo"), '运' to listOf("yun"),
    '忻' to listOf("xin"), '临' to listOf("lin"), '汾' to listOf("fen"),
    '吕' to listOf("lv"), '梁' to listOf("liang"),
    '呼' to listOf("hu"), '浩' to listOf("hao"), '特' to listOf("te"),
    '包' to listOf("bao"), '乌' to listOf("wu"), '赤' to listOf("chi"),
    '峰' to listOf("feng"), '通' to listOf("tong"), '辽' to listOf("liao"),
    '鄂' to listOf("e"), '多' to listOf("duo"), '斯' to listOf("si"),
    '伦' to listOf("lun"), '贝' to listOf("bei"), '巴' to listOf("ba"),
    '彦' to listOf("yan"), '淖' to listOf("nao"), '察' to listOf("cha"),
    '布' to listOf("bu"), '兴' to listOf("xing"), '安' to listOf("an"),
    '盟' to listOf("meng"), '锡' to listOf("xi"), '郭' to listOf("guo"),
    '勒' to listOf("le"), '阿' to listOf("a", "e"), '拉' to listOf("la"),
    '善' to listOf("shan"),
    '沈' to listOf("shen"), '连' to listOf("lian"), '鞍' to listOf("an"),
    '抚' to listOf("fu"), '顺' to listOf("shun"), '溪' to listOf("xi"),
    '丹' to listOf("dan"), '东' to listOf("dong"), '锦' to listOf("jin"),
    '营' to listOf("ying"), '阜' to listOf("fu"), '新' to listOf("xin"),
    '盘' to listOf("pan"), '铁' to listOf("tie"), '岭' to listOf("ling"),
    '朝' to listOf("chao", "zhao"), '葫' to listOf("hu"), '芦' to listOf("lu"),
    '四' to listOf("si"), '松' to listOf("song"), '源' to listOf("yuan"),
    '化' to listOf("hua"), '白' to listOf("bai"), '延' to listOf("yan"),
    '边' to listOf("bian"),
    '哈' to listOf("ha"), '滨' to listOf("bin"), '齐' to listOf("qi"),
    '鹤' to listOf("he"), '岗' to listOf("gang"), '双' to listOf("shuang"),
    '鸭' to listOf("ya"), '龙' to listOf("long"), '伊' to listOf("yi"),
    '春' to listOf("chun"), '佳' to listOf("jia"), '木' to listOf("mu"),
    '七' to listOf("qi"), '台' to listOf("tai"), '河' to listOf("he"),
    '黑' to listOf("hei"), '绥' to listOf("sui"),
    '无' to listOf("wu"), '徐' to listOf("xu"), '常' to listOf("chang"),
    '苏' to listOf("su"), '港' to listOf("gang"), '淮' to listOf("huai"),
    '盐' to listOf("yan"), '扬' to listOf("yang"), '镇' to listOf("zhen"),
    '泰' to listOf("tai"), '宿' to listOf("su", "xiu"), '迁' to listOf("qian"),
    '杭' to listOf("hang"), '波' to listOf("bo"), '温' to listOf("wen"),
    '嘉' to listOf("jia"), '湖' to listOf("hu"), '绍' to listOf("shao"),
    '金' to listOf("jin"), '华' to listOf("hua"), '衢' to listOf("qu"),
    '舟' to listOf("zhou"), '丽' to listOf("li"),
    '合' to listOf("he"), '肥' to listOf("fei"), '芜' to listOf("wu"),
    '蚌' to listOf("beng"), '埠' to listOf("bu"), '马' to listOf("ma"),
    '鞍' to listOf("an"), '铜' to listOf("tong"), '陵' to listOf("ling"),
    '黄' to listOf("huang"), '山' to listOf("shan"), '滁' to listOf("chu"),
    '阳' to listOf("yang"), '六' to listOf("liu", "lu"), '亳' to listOf("bo"),
    '池' to listOf("chi"), '宣' to listOf("xuan"),
    '福' to listOf("fu"), '厦' to listOf("xia", "sha"), '门' to listOf("men"),
    '莆' to listOf("pu"), '田' to listOf("tian"), '三' to listOf("san"),
    '明' to listOf("ming"), '泉' to listOf("quan"), '漳' to listOf("zhang"),
    '平' to listOf("ping"), '岩' to listOf("yan"), '德' to listOf("de"),
    '南' to listOf("nan"), '昌' to listOf("chang"), '景' to listOf("jing"),
    '镇' to listOf("zhen"), '萍' to listOf("ping"), '乡' to listOf("xiang"),
    '九' to listOf("jiu"), '江' to listOf("jiang"), '余' to listOf("yu"),
    '鹰' to listOf("ying"), '潭' to listOf("tan"), '赣' to listOf("gan"),
    '吉' to listOf("ji"), '宜' to listOf("yi"), '抚' to listOf("fu"),
    '饶' to listOf("rao"),
    '济' to listOf("ji"), '青' to listOf("qing"), '岛' to listOf("dao"),
    '淄' to listOf("zi"), '博' to listOf("bo"), '枣' to listOf("zao"),
    '东' to listOf("dong"), '营' to listOf("ying"), '烟' to listOf("yan"),
    '潍' to listOf("wei"), '坊' to listOf("fang"), '宁' to listOf("ning"),
    '威' to listOf("wei"), '海' to listOf("hai"), '日' to listOf("ri"),
    '照' to listOf("zhao"), '沂' to listOf("yi"), '聊' to listOf("liao"),
    '滨' to listOf("bin"), '菏' to listOf("he"), '泽' to listOf("ze"),
    '郑' to listOf("zheng"), '封' to listOf("feng"), '洛' to listOf("luo"),
    '顶' to listOf("ding"), '鹤' to listOf("he"), '壁' to listOf("bi"),
    '焦' to listOf("jiao"), '作' to listOf("zuo"), '濮' to listOf("pu"),
    '许' to listOf("xu"), '漯' to listOf("luo"), '峡' to listOf("xia"),
    '商' to listOf("shang"), '丘' to listOf("qiu"), '信' to listOf("xin"),
    '周' to listOf("zhou"), '口' to listOf("kou"), '驻' to listOf("zhu"),
    '马' to listOf("ma"), '店' to listOf("dian"), '源' to listOf("yuan"),
    '武' to listOf("wu"), '汉' to listOf("han"), '十' to listOf("shi"),
    '堰' to listOf("yan"), '襄' to listOf("xiang"), '鄂' to listOf("e"),
    '荆' to listOf("jing"), '孝' to listOf("xiao"), '感' to listOf("gan"),
    '冈' to listOf("gang"), '咸' to listOf("xian"), '随' to listOf("sui"),
    '恩' to listOf("en"), '施' to listOf("shi"),
    '株' to listOf("zhu"), '洲' to listOf("zhou"), '湘' to listOf("xiang"),
    '衡' to listOf("heng"), '邵' to listOf("shao"), '岳' to listOf("yue"),
    '常' to listOf("chang"), '界' to listOf("jie"), '益' to listOf("yi"),
    '郴' to listOf("chen"), '永' to listOf("yong"), '怀' to listOf("huai"),
    '化' to listOf("hua"), '娄' to listOf("lou"), '底' to listOf("di"),
    '西' to listOf("xi"),
    '广' to listOf("guang"), '韶' to listOf("shao"), '关' to listOf("guan"),
    '深' to listOf("shen"), '圳' to listOf("zhen"), '珠' to listOf("zhu"),
    '汕' to listOf("shan"), '佛' to listOf("fo", "fu"), '门' to listOf("men"),
    '湛' to listOf("zhan"), '茂' to listOf("mao"), '名' to listOf("ming"),
    '肇' to listOf("zhao"), '惠' to listOf("hui"), '梅' to listOf("mei"),
    '尾' to listOf("wei"), '河' to listOf("he"), '源' to listOf("yuan"),
    '清' to listOf("qing"), '远' to listOf("yuan"), '莞' to listOf("guan"),
    '中' to listOf("zhong"), '潮' to listOf("chao"), '揭' to listOf("jie"),
    '云' to listOf("yun"), '浮' to listOf("fu"),
    '宁' to listOf("ning"), '柳' to listOf("liu"), '桂' to listOf("gui"),
    '林' to listOf("lin"), '梧' to listOf("wu"), '防' to listOf("fang"),
    '钦' to listOf("qin"), '贵' to listOf("gui"), '玉' to listOf("yu"),
    '百' to listOf("bai", "bo"), '色' to listOf("se"), '贺' to listOf("he"),
    '池' to listOf("chi"), '来' to listOf("lai"), '宾' to listOf("bin"),
    '崇' to listOf("chong"), '左' to listOf("zuo"),
    '海' to listOf("hai"), '口' to listOf("kou"), '亚' to listOf("ya"),
    '沙' to listOf("sha"), '儋' to listOf("dan"), '五' to listOf("wu"),
    '指' to listOf("zhi"), '琼' to listOf("qiong"), '文' to listOf("wen"),
    '万' to listOf("wan"), '东' to listOf("dong"), '方' to listOf("fang"),
    '成' to listOf("cheng"), '都' to listOf("du", "dou"), '自' to listOf("zi"),
    '贡' to listOf("gong"), '攀' to listOf("pan"), '枝' to listOf("zhi"),
    '花' to listOf("hua"), '泸' to listOf("lu"), '阳' to listOf("yang"),
    '绵' to listOf("mian"), '元' to listOf("yuan"), '遂' to listOf("sui"),
    '内' to listOf("nei"), '江' to listOf("jiang"), '乐' to listOf("le", "yue"),
    '充' to listOf("chong"), '眉' to listOf("mei"), '宜' to listOf("yi"),
    '宾' to listOf("bin"), '安' to listOf("an"), '达' to listOf("da"),
    '雅' to listOf("ya"), '中' to listOf("zhong"), '资' to listOf("zi"),
    '坝' to listOf("ba"), '甘' to listOf("gan"), '孜' to listOf("zi"),
    '凉' to listOf("liang"),
    '贵' to listOf("gui"), '盘' to listOf("pan"), '遵' to listOf("zun"),
    '义' to listOf("yi"), '顺' to listOf("shun"), '毕' to listOf("bi"),
    '节' to listOf("jie"), '铜' to listOf("tong"), '仁' to listOf("ren"),
    '黔' to listOf("qian"),
    '昆' to listOf("kun"), '明' to listOf("ming"), '曲' to listOf("qu"),
    '靖' to listOf("jing"), '玉' to listOf("yu"), '溪' to listOf("xi"),
    '保' to listOf("bao"), '山' to listOf("shan"), '昭' to listOf("zhao"),
    '通' to listOf("tong"), '丽' to listOf("li"), '江' to listOf("jiang"),
    '普' to listOf("pu"), '洱' to listOf("er"), '沧' to listOf("cang"),
    '楚' to listOf("chu"), '雄' to listOf("xiong"), '红' to listOf("hong"),
    '版' to listOf("ban"), '纳' to listOf("na"), '理' to listOf("li"),
    '宏' to listOf("hong"), '怒' to listOf("nu"), '迪' to listOf("di"),
    '庆' to listOf("qing"),
    '拉' to listOf("la"), '萨' to listOf("sa"), '日' to listOf("ri"),
    '喀' to listOf("ka"), '则' to listOf("ze"), '昌' to listOf("chang"),
    '芝' to listOf("zhi"), '那' to listOf("na"),
    '铜' to listOf("tong"), '川' to listOf("chuan"), '宝' to listOf("bao"),
    '鸡' to listOf("ji"), '咸' to listOf("xian"), '渭' to listOf("wei"),
    '南' to listOf("nan"), '汉' to listOf("han"), '中' to listOf("zhong"),
    '榆' to listOf("yu"), '林' to listOf("lin"), '康' to listOf("kang"),
    '洛' to listOf("luo"),
    '兰' to listOf("lan"), '州' to listOf("zhou"), '嘉' to listOf("jia"),
    '峪' to listOf("yu"), '关' to listOf("guan"), '金' to listOf("jin"),
    '银' to listOf("yin"), '天' to listOf("tian"), '水' to listOf("shui"),
    '武' to listOf("wu"), '威' to listOf("wei"), '张' to listOf("zhang"),
    '掖' to listOf("ye"), '凉' to listOf("liang"), '酒' to listOf("jiu"),
    '泉' to listOf("quan"), '阳' to listOf("yang"), '西' to listOf("xi"),
    '陇' to listOf("long"), '南' to listOf("nan"), '夏' to listOf("xia"),
    '川' to listOf("chuan"),
    '宁' to listOf("ning"), '海' to listOf("hai"), '东' to listOf("dong"),
    '北' to listOf("bei"), '黄' to listOf("huang"), '南' to listOf("nan"),
    '果' to listOf("guo"), '洛' to listOf("luo"), '树' to listOf("shu"),
    '西' to listOf("xi"),
    '银' to listOf("yin"), '川' to listOf("chuan"), '石' to listOf("shi"),
    '嘴' to listOf("zui"), '吴' to listOf("wu"), '忠' to listOf("zhong"),
    '固' to listOf("gu"), '原' to listOf("yuan"), '卫' to listOf("wei"),
    '乌' to listOf("wu"), '鲁' to listOf("lu"), '木' to listOf("mu"),
    '齐' to listOf("qi"), '克' to listOf("ke"), '拉' to listOf("la"),
    '玛' to listOf("ma"), '依' to listOf("yi"), '吐' to listOf("tu"),
    '番' to listOf("fan"), '密' to listOf("mi"), '哈' to listOf("ha"),
    '昌' to listOf("chang"), '吉' to listOf("ji"), '博' to listOf("bo"),
    '尔' to listOf("er"), '塔' to listOf("ta"), '彦' to listOf("yan"),
    '郭' to listOf("guo"), '楞' to listOf("leng"), '苏' to listOf("su"),
    '孜' to listOf("zi"), '勒' to listOf("le"), '什' to listOf("shen", "shi"),
    '和' to listOf("he"), '田' to listOf("tian"), '伊' to listOf("yi"),
    '犁' to listOf("li"), '城' to listOf("cheng"), '阿' to listOf("a", "e"),
    '勒' to listOf("le"), '泰' to listOf("tai"), '河' to listOf("he"),
    '子' to listOf("zi"), '图' to listOf("tu"), '舒' to listOf("shu"),
    '渠' to listOf("qu"), '屯' to listOf("tun"), '门' to listOf("men"),
    '双' to listOf("shuang"), '可' to listOf("ke"), '达' to listOf("da"),
    '昆' to listOf("kun"), '玉' to listOf("yu"), '胡' to listOf("hu"),
    '杨' to listOf("yang"), '新' to listOf("xin"), '星' to listOf("xing"),
    // —— 单测覆盖检查补字 ——
    '牡' to listOf("mu"), // 牡丹江
    '浙' to listOf("zhe"), // 浙江省
    '里' to listOf("li"), // 阿里
    '音' to listOf("yin"), // 巴音郭楞
)

/** 汉字 → 读音候选（同字多次出现时读音取并集，避免多音字被覆盖） */
private val PINYIN_READINGS: Map<Char, List<String>> =
    buildMap<Char, LinkedHashSet<String>> {
        PINYIN_RAW.forEach { (ch, reads) ->
            getOrPut(ch) { LinkedHashSet() }.addAll(reads)
        }
    }.mapValues { it.value.toList() }

/** 单个汉字的读音候选（字典缺失时降级为字符本身，保证不崩） */
private fun readingsOf(ch: Char): List<String> =
    PINYIN_READINGS[ch] ?: listOf(ch.toString())

/**
 * 展开一个汉字串的全部「全拼」候选（多音字做笛卡尔积，去重，有上限保护）。
 * 例：「长沙」→ ["changsha", "zhangsha"]
 */
fun pinyinFullVariants(text: String): List<String> {
    var variants = listOf("")
    for (ch in text) {
        val readings = readingsOf(ch)
        variants = variants.flatMap { prefix -> readings.map { prefix + it } }
            .distinct()
            .take(32) // 多音字组合数量很小，封顶仅为保护
    }
    return variants
}

/**
 * 展开一个汉字串的全部「首字母」候选。
 * 例：「长沙」→ ["cs", "zs"]；「鄂尔多斯」→ ["eeds"]
 */
fun pinyinInitialsVariants(text: String): List<String> {
    var variants = listOf("")
    for (ch in text) {
        val readings = readingsOf(ch)
        variants = variants.flatMap { prefix ->
            readings.map { prefix + it.first() }
        }.distinct().take(32)
    }
    return variants
}
