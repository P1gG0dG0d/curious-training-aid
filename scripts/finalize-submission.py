from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=root/'提交包/开源与AI使用声明.md'
s=p.read_text(encoding='utf-8')
marker='\n## 真实脱敏对话原文附录\n'
s=s.split(marker)[0]
s += marker + '\n以下收录主窗口与7个关联子窗口的可获取记录，截至北京时间2026-10-18 17:20。属于有明确删节标记的脱敏副本，不是无删节原件。系统提示、内部推理、部分工具大文本与附件原图不提供；另一个同源fork分支未纳入本次导出，出图窗口与其他独立开发窗口仍需队伍核对补充。\n'
folder=root/'提交包/对话记录'
for item in sorted(folder.glob('*.md'),key=lambda p:(p.name!='README.md',p.name)):
    s+='\n\n### 记录文件：'+item.name+'\n\n'+item.read_text(encoding='utf-8')
p.write_text(s,encoding='utf-8')
print('Appended actual redacted records:',len(s),'characters')
