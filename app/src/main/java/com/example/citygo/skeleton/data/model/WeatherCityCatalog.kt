package com.example.citygo.skeleton.data.model

/* =====================================================================================
 * 全国天气城市目录
 *
 * 覆盖全国 4 个直辖市 + 全部地级市 / 自治州 / 盟（含新疆兵团主要城市），
 * 每条携带高德天气接口查询所需的 6 位行政区划 adCode。
 *
 * 用途：
 * - 天气页头部「热门城市」快捷下拉使用 [HOT_WEATHER_CITIES]；
 * - 「搜索其他城市」通过 [searchWeatherCities] 在本目录内本地过滤，
 *   不依赖网络，任何环境（Mock / 真实 Key）都可搜索；
 * - 选中城市后 ViewModel 以 adCode 调高德 weatherInfo，支持全国城市天气查询。
 *
 * 数据维护：后续如需细化到区县，可在本文件补充区县级条目（adCode 精确到 6 位区县码）。
 * ===================================================================================== */

/**
 * 城市目录条目（在 [WeatherCity] 基础上附加省份，用于搜索结果展示归属）。
 *
 * 构造时预计算城市名与省份的全拼 / 首字母变体，搜索时直接匹配，无运行时开销。
 */
data class WeatherCityEntry(
    val name: String,
    val adCode: String,
    val province: String,
) {
    /** 城市名全拼候选，如长沙 → ["changsha", "zhangsha"] */
    val namePinyin: List<String> = pinyinFullVariants(name)

    /** 城市名首字母候选，如长沙 → ["cs", "zs"] */
    val nameInitials: List<String> = pinyinInitialsVariants(name)

    /** 省份全拼候选，如"广西壮族自治区"含 "guangxi" 前缀，便于按省份搜 */
    val provincePinyin: List<String> = pinyinFullVariants(province)

    fun toWeatherCity(): WeatherCity = WeatherCity(name = name, adCode = adCode)
}

/** 省份 → 城市（名称 to adCode）的紧凑构造器 */
private fun cities(province: String, vararg pairs: Pair<String, String>): List<WeatherCityEntry> =
    pairs.map { (name, adCode) -> WeatherCityEntry(name = name, adCode = adCode, province = province) }

/**
 * 全国城市总目录（约 340 个地级行政区）。
 * adCode 采用国标行政区划代码（与高德 adcode 一致）。
 */
val ALL_WEATHER_CITY_ENTRIES: List<WeatherCityEntry> = buildList {
    addAll(cities("北京市", "北京" to "110000"))
    addAll(cities("天津市", "天津" to "120000"))
    addAll(cities("上海市", "上海" to "310000"))
    addAll(cities("重庆市", "重庆" to "500000"))

    addAll(cities("河北省",
        "石家庄" to "130100", "唐山" to "130200", "秦皇岛" to "130300", "邯郸" to "130400",
        "邢台" to "130500", "保定" to "130600", "张家口" to "130700", "承德" to "130800",
        "沧州" to "130900", "廊坊" to "131000", "衡水" to "131100",
    ))
    addAll(cities("山西省",
        "太原" to "140100", "大同" to "140200", "阳泉" to "140300", "长治" to "140400",
        "晋城" to "140500", "朔州" to "140600", "晋中" to "140700", "运城" to "140800",
        "忻州" to "140900", "临汾" to "141000", "吕梁" to "141100",
    ))
    addAll(cities("内蒙古自治区",
        "呼和浩特" to "150100", "包头" to "150200", "乌海" to "150300", "赤峰" to "150400",
        "通辽" to "150500", "鄂尔多斯" to "150600", "呼伦贝尔" to "150700", "巴彦淖尔" to "150800",
        "乌兰察布" to "150900", "兴安盟" to "152200", "锡林郭勒盟" to "152500", "阿拉善盟" to "152900",
    ))
    addAll(cities("辽宁省",
        "沈阳" to "210100", "大连" to "210200", "鞍山" to "210300", "抚顺" to "210400",
        "本溪" to "210500", "丹东" to "210600", "锦州" to "210700", "营口" to "210800",
        "阜新" to "210900", "辽阳" to "211000", "盘锦" to "211100", "铁岭" to "211200",
        "朝阳" to "211300", "葫芦岛" to "211400",
    ))
    addAll(cities("吉林省",
        "长春" to "220100", "吉林" to "220200", "四平" to "220300", "辽源" to "220400",
        "通化" to "220500", "白山" to "220600", "松原" to "220700", "白城" to "220800",
        "延边" to "222400",
    ))
    addAll(cities("黑龙江省",
        "哈尔滨" to "230100", "齐齐哈尔" to "230200", "鸡西" to "230300", "鹤岗" to "230400",
        "双鸭山" to "230500", "大庆" to "230600", "伊春" to "230700", "佳木斯" to "230800",
        "七台河" to "230900", "牡丹江" to "231000", "黑河" to "231100", "绥化" to "231200",
        "大兴安岭" to "232700",
    ))
    addAll(cities("江苏省",
        "南京" to "320100", "无锡" to "320200", "徐州" to "320300", "常州" to "320400",
        "苏州" to "320500", "南通" to "320600", "连云港" to "320700", "淮安" to "320800",
        "盐城" to "320900", "扬州" to "321000", "镇江" to "321100", "泰州" to "321200",
        "宿迁" to "321300",
    ))
    addAll(cities("浙江省",
        "杭州" to "330100", "宁波" to "330200", "温州" to "330300", "嘉兴" to "330400",
        "湖州" to "330500", "绍兴" to "330600", "金华" to "330700", "衢州" to "330800",
        "舟山" to "330900", "台州" to "331000", "丽水" to "331100",
    ))
    addAll(cities("安徽省",
        "合肥" to "340100", "芜湖" to "340200", "蚌埠" to "340300", "淮南" to "340400",
        "马鞍山" to "340500", "淮北" to "340600", "铜陵" to "340700", "安庆" to "340800",
        "黄山" to "341000", "滁州" to "341100", "阜阳" to "341200", "宿州" to "341300",
        "六安" to "341500", "亳州" to "341600", "池州" to "341700", "宣城" to "341800",
    ))
    addAll(cities("福建省",
        "福州" to "350100", "厦门" to "350200", "莆田" to "350300", "三明" to "350400",
        "泉州" to "350500", "漳州" to "350600", "南平" to "350700", "龙岩" to "350800",
        "宁德" to "350900",
    ))
    addAll(cities("江西省",
        "南昌" to "360100", "景德镇" to "360200", "萍乡" to "360300", "九江" to "360400",
        "新余" to "360500", "鹰潭" to "360600", "赣州" to "360700", "吉安" to "360800",
        "宜春" to "360900", "抚州" to "361000", "上饶" to "361100",
    ))
    addAll(cities("山东省",
        "济南" to "370100", "青岛" to "370200", "淄博" to "370300", "枣庄" to "370400",
        "东营" to "370500", "烟台" to "370600", "潍坊" to "370700", "济宁" to "370800",
        "泰安" to "370900", "威海" to "371000", "日照" to "371100", "临沂" to "371300",
        "德州" to "371400", "聊城" to "371500", "滨州" to "371600", "菏泽" to "371700",
    ))
    addAll(cities("河南省",
        "郑州" to "410100", "开封" to "410200", "洛阳" to "410300", "平顶山" to "410400",
        "安阳" to "410500", "鹤壁" to "410600", "新乡" to "410700", "焦作" to "410800",
        "濮阳" to "410900", "许昌" to "411000", "漯河" to "411100", "三门峡" to "411200",
        "南阳" to "411300", "商丘" to "411400", "信阳" to "411500", "周口" to "411600",
        "驻马店" to "411700", "济源" to "419001",
    ))
    addAll(cities("湖北省",
        "武汉" to "420100", "黄石" to "420200", "十堰" to "420300", "宜昌" to "420500",
        "襄阳" to "420600", "鄂州" to "420700", "荆门" to "420800", "孝感" to "420900",
        "荆州" to "421000", "黄冈" to "421100", "咸宁" to "421200", "随州" to "421300",
        "恩施" to "422800",
    ))
    addAll(cities("湖南省",
        "长沙" to "430100", "株洲" to "430200", "湘潭" to "430300", "衡阳" to "430400",
        "邵阳" to "430500", "岳阳" to "430600", "常德" to "430700", "张家界" to "430800",
        "益阳" to "430900", "郴州" to "431000", "永州" to "431100", "怀化" to "431200",
        "娄底" to "431300", "湘西" to "433100",
    ))
    addAll(cities("广东省",
        "广州" to "440100", "韶关" to "440200", "深圳" to "440300", "珠海" to "440400",
        "汕头" to "440500", "佛山" to "440600", "江门" to "440700", "湛江" to "440800",
        "茂名" to "440900", "肇庆" to "441200", "惠州" to "441300", "梅州" to "441400",
        "汕尾" to "441500", "河源" to "441600", "阳江" to "441700", "清远" to "441800",
        "东莞" to "441900", "中山" to "442000", "潮州" to "445100", "揭阳" to "445200",
        "云浮" to "445300",
    ))
    addAll(cities("广西壮族自治区",
        "南宁" to "450100", "柳州" to "450200", "桂林" to "450300", "梧州" to "450400",
        "北海" to "450500", "防城港" to "450600", "钦州" to "450700", "贵港" to "450800",
        "玉林" to "450900", "百色" to "451000", "贺州" to "451100", "河池" to "451200",
        "来宾" to "451300", "崇左" to "451400",
    ))
    addAll(cities("海南省",
        "海口" to "460100", "三亚" to "460200", "三沙" to "460300", "儋州" to "460400",
        "五指山" to "469001", "琼海" to "469002", "文昌" to "469005", "万宁" to "469006",
        "东方" to "469007",
    ))
    addAll(cities("四川省",
        "成都" to "510100", "自贡" to "510300", "攀枝花" to "510400", "泸州" to "510500",
        "德阳" to "510600", "绵阳" to "510700", "广元" to "510800", "遂宁" to "510900",
        "内江" to "511000", "乐山" to "511100", "南充" to "511300", "眉山" to "511400",
        "宜宾" to "511500", "广安" to "511600", "达州" to "511700", "雅安" to "511800",
        "巴中" to "511900", "资阳" to "512000", "阿坝" to "513200", "甘孜" to "513300",
        "凉山" to "513400",
    ))
    addAll(cities("贵州省",
        "贵阳" to "520100", "六盘水" to "520200", "遵义" to "520300", "安顺" to "520400",
        "毕节" to "520500", "铜仁" to "520600", "黔西南" to "522300", "黔东南" to "522600",
        "黔南" to "522700",
    ))
    addAll(cities("云南省",
        "昆明" to "530100", "曲靖" to "530300", "玉溪" to "530400", "保山" to "530500",
        "昭通" to "530600", "丽江" to "530700", "普洱" to "530800", "临沧" to "530900",
        "楚雄" to "532300", "红河" to "532500", "文山" to "532600", "西双版纳" to "532800",
        "大理" to "532900", "德宏" to "533100", "怒江" to "533300", "迪庆" to "533400",
    ))
    addAll(cities("西藏自治区",
        "拉萨" to "540100", "日喀则" to "540200", "昌都" to "540300", "林芝" to "540400",
        "山南" to "540500", "那曲" to "540600", "阿里" to "542500",
    ))
    addAll(cities("陕西省",
        "西安" to "610100", "铜川" to "610200", "宝鸡" to "610300", "咸阳" to "610400",
        "渭南" to "610500", "延安" to "610600", "汉中" to "610700", "榆林" to "610800",
        "安康" to "610900", "商洛" to "611000",
    ))
    addAll(cities("甘肃省",
        "兰州" to "620100", "嘉峪关" to "620200", "金昌" to "620300", "白银" to "620400",
        "天水" to "620500", "武威" to "620600", "张掖" to "620700", "平凉" to "620800",
        "酒泉" to "620900", "庆阳" to "621000", "定西" to "621100", "陇南" to "621200",
        "临夏" to "622900", "甘南" to "623000",
    ))
    addAll(cities("青海省",
        "西宁" to "630100", "海东" to "630200", "海北" to "632200", "黄南" to "632300",
        "海南州" to "632500", "果洛" to "632600", "玉树" to "632700", "海西" to "632800",
    ))
    addAll(cities("宁夏回族自治区",
        "银川" to "640100", "石嘴山" to "640200", "吴忠" to "640300", "固原" to "640400",
        "中卫" to "640500",
    ))
    addAll(cities("新疆维吾尔自治区",
        "乌鲁木齐" to "650100", "克拉玛依" to "650200", "吐鲁番" to "650400", "哈密" to "650500",
        "昌吉" to "652300", "博尔塔拉" to "652700", "巴音郭楞" to "652800", "阿克苏" to "652900",
        "克孜勒苏" to "653000", "喀什" to "653100", "和田" to "653200", "伊犁" to "654000",
        "塔城" to "654200", "阿勒泰" to "654300", "石河子" to "659001", "阿拉尔" to "659002",
        "图木舒克" to "659003", "五家渠" to "659004", "北屯" to "659005", "铁门关" to "659006",
        "双河" to "659007", "可克达拉" to "659008", "昆玉" to "659009", "胡杨河" to "659010",
        "新星" to "659011",
    ))
    // 香港 / 澳门（高德天气接口支持情况以线上为准，暂不纳入，避免查询失败）
}

/**
 * 热门城市：头部下拉快速切换（与搜索页空态推荐共用）。
 */
val HOT_WEATHER_CITIES: List<WeatherCity> = listOf(
    WeatherCity("长沙", "430100"),
    WeatherCity("成都", "510100"),
    WeatherCity("北京", "110000"),
    WeatherCity("上海", "310000"),
    WeatherCity("广州", "440100"),
    WeatherCity("杭州", "330100"),
    WeatherCity("重庆", "500000"),
    WeatherCity("西安", "610100"),
)

/**
 * 在全国城市目录中搜索城市（本地过滤，即时返回，无需网络；不依赖中文输入法）。
 *
 * 支持的关键字形式：
 * 1. 汉字城市名 / 省份名：如「苏州」「云南」；
 * 2. 全拼（不带音调）：如 suzhou、changsha、neimenggu；
 * 3. 首字母缩写：如 sz、cs；
 * 4. adCode 数字：如 3205。
 *
 * 匹配后按相关度排序（越靠前越可能是用户目标）：
 * 城市名前缀命中 → 首字母精确/前缀 → 城市全拼/汉字包含 → 省份命中 → adCode。
 *
 * 关键字为空时返回热门城市。
 *
 * @param limit 最多返回条数，避免超长结果影响列表性能
 */
fun searchWeatherCities(keyword: String, limit: Int = 50): List<WeatherCityEntry> {
    val kw = keyword.trim()
    if (kw.isEmpty()) {
        return HOT_WEATHER_CITIES.mapNotNull { hot ->
            ALL_WEATHER_CITY_ENTRIES.firstOrNull { it.adCode == hot.adCode }
        }
    }

    // —— 数字：按 adCode 前缀匹配 ——
    val digits = kw.filter(Char::isDigit)
    if (digits.isNotEmpty() && kw.all(Char::isDigit)) {
        return ALL_WEATHER_CITY_ENTRIES
            .asSequence()
            .filter { it.adCode.startsWith(digits) }
            .sortedBy { it.adCode }
            .take(limit)
            .toList()
    }

    // —— 纯英文字母：拼音全拼 / 首字母 / 省份拼音匹配 ——
    val isAsciiLetters = kw.isNotEmpty() && kw.all { it.isLetter() && it.code < 128 }
    if (isAsciiLetters) {
        val q = kw.lowercase()
        // 相关度档位：0 最相关
        fun rankOf(entry: WeatherCityEntry): Int? = when {
            // 城市全拼前缀：suzhou 命中苏州
            entry.namePinyin.any { it.startsWith(q) } -> 0
            // 首字母精确（sz 通常比 sz... 前缀更明确，放同一档）
            entry.nameInitials.any { it == q } -> 0
            // 首字母前缀
            entry.nameInitials.any { it.startsWith(q) } -> 1
            // 城市全拼包含
            entry.namePinyin.any { it.contains(q) } -> 2
            // 省份全拼前缀：yunnan 命中云南全省
            entry.provincePinyin.any { it.startsWith(q) } -> 3
            // 省份全拼包含：guangxi 命中广西
            entry.provincePinyin.any { it.contains(q) } -> 4
            else -> null
        }
        return ALL_WEATHER_CITY_ENTRIES
            .mapNotNull { entry -> rankOf(entry)?.let { rank -> rank to entry } }
            .sortedWith(
                // 档位优先；同档按首字母与全拼长度（短名更可能是目标），再按 adCode 稳定排序
                compareBy<Pair<Int, WeatherCityEntry>> { it.first }
                    .thenBy { it.second.nameInitials.firstOrNull()?.length ?: 0 }
                    .thenBy { it.second.namePinyin.first().length }
                    .thenBy { it.second.adCode },
            )
            .take(limit)
            .map { it.second }
    }

    // —— 汉字（或混合）：按城市名 / 省份名直接匹配 ——
    return ALL_WEATHER_CITY_ENTRIES
        .asSequence()
        .filter { entry -> entry.name.contains(kw) || entry.province.contains(kw) }
        // 城市名开头命中的排最前，其次名字短的，最后按 adCode
        .sortedWith(
            compareBy<WeatherCityEntry> { !it.name.startsWith(kw) }
                .thenBy { it.name.length }
                .thenBy { it.adCode },
        )
        .take(limit)
        .toList()
}
