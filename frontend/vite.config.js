// 上海如静知华信息科技有限公司 https://www.zhuatech.cn/
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
export default defineConfig({plugins:[vue()],server:{proxy:{'/api':{target:process.env.API_TARGET || 'http://127.0.0.1:8080',changeOrigin:true}}}});
