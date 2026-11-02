package com.vrgames.clockshop.content

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text
import com.vrgames.clockshop.CLUES_ID
import com.vrgames.clockshop.GUIDE_ID
import com.vrgames.clockshop.game.Game

private val CardBg = Color(0x26F4EEE2)

/** 线索簿: everything found so far, the pictures large, and the four parts. */
@Composable
fun CluesWindow() {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val s = Game.shop
    val context = LocalContext.current
    val cache = remember { HashMap<String, ImageBitmap?>() }
    Column(
        Modifier.fillMaxSize().background(WindowBg).padding(28.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WindowHeader(CLUES_ID, "线索簿", 32.sp)
        Text("怀表零件：" + (1..4).joinToString("　") { if (it in s.parts) "第 $it 枚 ●" else "第 $it 枚 ○" }, fontSize = 20.sp, color = LabelInk)
        Text("找到的线索", fontSize = 24.sp, color = Jade)
        for (clue in s.clues) {
            Text("· $clue", fontSize = 19.sp, color = LabelInk)
        }
        Text("图片", fontSize = 24.sp, color = Jade)
        for ((name, label) in seenPictures(s)) {
            val bmp = cache.getOrPut(name) {
                runCatching { context.assets.open("textures/$name.jpg").use { BitmapFactory.decodeStream(it).asImageBitmap() } }.getOrNull()
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, fontSize = 20.sp, color = Brass)
                if (bmp != null) Image(bmp, label, Modifier.fillMaxWidth().height(if (name == "sketch") 420.dp else 520.dp), contentScale = ContentScale.Fit)
            }
        }
        Text("铺子、师傅和照片里的人物都是本作虚构的；照片由 AI 生成。", fontSize = 15.sp, color = Dim)
    }
}

/** 钟表图鉴: a true short story for each kind of clock in the shop. */
@Composable
fun GuideWindow() {
    Column(
        Modifier.fillMaxSize().background(WindowBg).padding(28.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        WindowHeader(GUIDE_ID, "钟表图鉴", 32.sp)
        Text("铺子里每一种钟，都有一段真实的来历。", fontSize = 18.sp, color = Dim)
        for ((title, text) in GUIDE) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, fontSize = 24.sp, color = Brass)
                Text(text, fontSize = 18.sp, color = LabelInk)
            }
        }
    }
}

private val GUIDE = listOf(
    "摆钟" to "1656 年，荷兰科学家惠更斯造出了第一座摆钟。钟摆来回一次的时间几乎不变，机械钟每天的误差从大约一刻钟一下子缩小到几十秒以内。",
    "落地大钟" to "17 世纪后期，英国钟表匠用上了“锚式擒纵机构”，钟摆可以做得很长、摆幅很小，于是有了把长钟摆藏在高高木箱里的落地钟。英文叫它“祖父钟”，名字来自 1876 年的一首歌《我祖父的钟》。",
    "擒纵机构" to "钟表里“嘀嗒”声的来源。它像一道闸门，让齿轮每次只能前进一小格，再被钟摆或摆轮放开，这样指针才会一格一格地均匀走动。",
    "发条" to "卷紧的钢片慢慢松开，就能推着齿轮转动。有了发条，钟表不再需要挂重锤，才能做小、带在身上。",
    "怀表" to "16 世纪初，德国纽伦堡已经有能随身携带的小钟表。后来男士开始穿马甲，怀表就挂在表链上、揣进马甲口袋，流行了三百多年，直到手表普及。",
    "八音盒" to "1796 年，瑞士钟表匠安托万·法布尔发明了八音盒：转动的滚筒上有许多小凸点，拨动一排长短不同的钢梳片，就奏出了曲子。",
    "自鸣钟进中国" to "明朝万历年间（1601 年），传教士利玛窦把会自己报时的“自鸣钟”献给皇帝。清朝宫廷专门设立了“做钟处”，制作和修理钟表，故宫至今收藏着许多精美的古钟。",
    "天文钟" to "1088 年，北宋的苏颂和韩公廉建成“水运仪象台”，用水力带动，能演示天象、自动报时，其中的“天衡”装置被认为是擒纵机构的先驱。欧洲著名的布拉格天文钟建于 1410 年，至今还在走。",
)
