// Vue3 应用入口：全局注册 Pinia、vue-router 与 Element Plus（中文语言包）
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn.mjs'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import './style.css'

const app = createApp(App)

// 注意：Pinia 必须先于 router 安装，路由守卫中才可以正常使用 store
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
