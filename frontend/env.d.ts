/// <reference types="vite/client" />
// 手动添加以下声明，告诉 TS 如何处理 .vue 文件
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  // eslint-disable-next-line @typescript-eslint/no-explicit-any, @typescript-eslint/ban-types
  const component: DefineComponent<{}, {}, any>
  export default component
}
