/**
 * nav.ts — 导航/模块键类型（M4-1：由散落各处的字面量联合上移）
 *
 * 此前 `'bookmarks' | 'notes' | 'files' | 'vault'` 这段联合在 22 处被逐字重复
 * （shared/layout 的 MainLayout / SideNav / MobileTabBar / AppHeader、modules 的
 * Home / ModuleToolbar / RecycleLayout、以及 useAppShortcuts / useSearchActions 等），
 * 增删一个模块要全文搜索替换。集中到这里后，各处只引类型名。
 *
 * 注意与 `shared/layout/navConfig.ts` 的 NavKey 区分：
 * - ModuleKey：主壳内的 4 个业务模块，走 emit('module-change') 在壳内切换
 * - NavJumpKey：ModuleKey 再加 'music'，用于跨模块跳转（搜索面板「跳到音乐」）
 * - NavKey：侧栏/移动端 TabBar 的全量可配置导航项（含概览/回收站/下载中心等独立路由）
 */

/** 主壳内 4 个业务模块 */
export type ModuleKey = 'bookmarks' | 'notes' | 'files' | 'vault'

/** 可跳转目标：4 个模块 + 音乐（音乐是独立路由，不参与模块切换） */
export type NavJumpKey = ModuleKey | 'music'

/** 主壳模块的默认顺序 */
export const MODULE_KEYS: ModuleKey[] = ['bookmarks', 'notes', 'files', 'vault']
