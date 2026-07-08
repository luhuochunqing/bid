# -*- coding: utf-8 -*-
import json
import random
import datetime

PROJECTS = [
    {"name": "西安地铁10号线通信系统项目", "category": "轨道交通"},
    {"name": "国家电网智能变电站项目", "category": "电力"},
    {"name": "中国移动5G基站建设项目", "category": "通信"},
    {"name": "京东物流仓储自动化项目", "category": "物流"},
    {"name": "华为云计算数据中心项目", "category": "云计算"},
    {"name": "中国石化油气管道项目", "category": "能源"},
    {"name": "腾讯大数据平台项目", "category": "大数据"},
    {"name": "阿里巴巴智慧园区项目", "category": "智慧园区"},
    {"name": "百度自动驾驶测试基地项目", "category": "自动驾驶"},
    {"name": "美团外卖配送系统项目", "category": "外卖"},
    {"name": "滴滴出行智慧交通项目", "category": "交通"},
    {"name": "顺丰速运冷链物流项目", "category": "冷链"},
    {"name": "小米智能家居平台项目", "category": "智能家居"},
    {"name": "OPPO智能终端项目", "category": "智能终端"},
    {"name": "vivo研发中心项目", "category": "研发"},
    {"name": "蔚来新能源汽车项目", "category": "新能源"},
    {"name": "小鹏汽车智能座舱项目", "category": "智能座舱"},
    {"name": "理想汽车动力系统项目", "category": "动力系统"},
    {"name": "比亚迪电池工厂项目", "category": "电池"},
    {"name": "宁德时代储能项目", "category": "储能"},
    {"name": "中芯国际芯片制造项目", "category": "芯片"},
    {"name": "台积电晶圆厂项目", "category": "晶圆"},
    {"name": "紫光存储芯片项目", "category": "存储"},
    {"name": "长江存储3D NAND项目", "category": "3D NAND"},
    {"name": "华为鲲鹏服务器项目", "category": "服务器"},
    {"name": "浪潮AI服务器项目", "category": "AI服务器"},
    {"name": "联想数据中心项目", "category": "数据中心"},
    {"name": "戴尔PowerEdge项目", "category": "PowerEdge"},
    {"name": "惠普企业级服务器项目", "category": "企业级"},
    {"name": "IBM Z系列主机项目", "category": "主机"},
    {"name": "招商银行核心银行系统项目", "category": "银行"},
    {"name": "工商银行数字化转型项目", "category": "数字化"},
    {"name": "建设银行智慧银行项目", "category": "智慧银行"},
    {"name": "农业银行金融科技项目", "category": "金融科技"},
    {"name": "中国银行跨境支付项目", "category": "跨境支付"},
    {"name": "平安保险数字化平台项目", "category": "保险"},
    {"name": "太平洋保险核心系统项目", "category": "核心系统"},
    {"name": "中国人寿智能客服项目", "category": "智能客服"},
    {"name": "中国人保大数据项目", "category": "人保"},
    {"name": "中信证券交易系统项目", "category": "证券"},
    {"name": "华泰证券量化交易项目", "category": "量化"},
    {"name": "海通证券资管系统项目", "category": "资管"},
    {"name": "国泰君安风控系统项目", "category": "风控"},
    {"name": "银河证券投顾系统项目", "category": "投顾"},
    {"name": "中国移动CRM系统项目", "category": "CRM"},
    {"name": "中国电信计费系统项目", "category": "计费"},
    {"name": "中国联通网络管理项目", "category": "网管"},
    {"name": "中国铁塔基站运维项目", "category": "运维"},
    {"name": "中国广电5G项目", "category": "5G"},
    {"name": "南方电网调度系统项目", "category": "调度"},
    {"name": "国家电网计量系统项目", "category": "计量"},
    {"name": "华能集团新能源项目", "category": "新能源"},
    {"name": "大唐集团电力项目", "category": "电力"},
    {"name": "国电投核电项目", "category": "核电"},
    {"name": "中核集团核工程项目", "category": "核工程"},
    {"name": "中铁建设工程项目", "category": "工程"},
    {"name": "中建集团建筑项目", "category": "建筑"},
    {"name": "中国交建桥梁项目", "category": "桥梁"},
    {"name": "中国中铁隧道项目", "category": "隧道"},
    {"name": "中国电建水电项目", "category": "水电"},
    {"name": "中国能建火电项目", "category": "火电"},
    {"name": "中国中车轨道交通项目", "category": "轨交"},
    {"name": "中国通号信号系统项目", "category": "信号"},
    {"name": "中国中冶冶金项目", "category": "冶金"},
    {"name": "中国建筑设计院项目", "category": "设计"},
]

TECH_SECTIONS = [
    "总体技术方案", "系统架构设计", "网络拓扑设计", "服务器部署方案",
    "数据库设计", "安全方案", "高可用方案", "容灾备份方案",
    "性能优化方案", "接口设计", "API设计", "数据迁移方案",
    "系统集成方案", "测试方案", "运维方案", "监控方案",
    "部署方案", "培训方案", "技术支持方案", "升级方案",
    "开发技术选型", "编程语言选择", "框架选择", "中间件选型",
    "前端技术方案", "后端技术方案", "移动端技术方案", "物联网方案",
    "人工智能方案", "大数据方案", "云计算方案", "边缘计算方案",
    "微服务架构", "容器化方案", "DevOps方案", "CI/CD方案",
]

COMMERCIAL_SECTIONS = [
    "商务报价方案", "价格策略", "付款方式", "交付周期",
    "售后服务承诺", "质保期承诺", "培训服务", "技术支持",
    "项目管理方案", "实施计划", "进度安排", "资源配置",
    "风险管理方案", "质量保证方案", "合规性说明", "资质文件",
    "业绩案例", "客户评价", "团队介绍", "公司实力",
]

OTHER_SECTIONS = [
    "项目概述", "需求分析", "解决方案", "实施路线",
    "验收标准", "项目里程碑", "技术文档", "操作手册",
]

SECTION_CONTENT_TEMPLATES = {
    "技术": [
        "本方案采用先进的{}技术，结合{}架构，实现{}功能。系统具备{}特性，能够满足{}需求。通过{}手段，确保{}目标达成。",
        "根据项目需求，我们设计了{}方案，采用{}技术栈，实现{}效果。系统架构包括{}模块，各模块之间通过{}方式交互。",
        "针对{}场景，我们提出了{}解决方案，通过{}技术实现{}功能。方案具有{}优势，能够{}。",
        "系统设计遵循{}原则，采用{}架构模式，确保{}性能。主要技术包括{}、{}和{}，能够{}。",
        "本技术方案涵盖{}内容，采用{}方法，实现{}目标。方案具有{}特点，能够{}需求。",
    ],
    "商务": [
        "我们提供{}商务方案，包括{}服务内容。价格合理，服务优质，能够{}。",
        "根据{}需求，我们制定了{}商务策略，确保{}目标达成。服务包括{}、{}和{}。",
        "商务报价基于{}标准，提供{}服务承诺。付款方式灵活，能够{}。",
        "售后服务承诺包括{}内容，质保期为{}。提供{}培训和{}技术支持。",
        "项目管理采用{}方法，确保{}进度。资源配置合理，能够{}。",
    ],
}

def generate_text_preview(project_name, section_title, label):
    category = "技术" if label == "技术" else "商务"
    templates = SECTION_CONTENT_TEMPLATES[category]
    template = random.choice(templates)
    
    if category == "技术":
        tech_words = ["微服务", "云计算", "大数据", "人工智能", "物联网", 
                      "区块链", "边缘计算", "容器化", "DevOps", "API"]
        return template.format(
            random.choice(tech_words),
            random.choice(["分布式", "模块化", "面向服务"]),
            random.choice(["高可用", "高性能", "高安全"]),
            random.choice(["弹性伸缩", "自动故障转移", "实时监控"]),
            project_name,
            random.choice(["优化", "升级", "改造"]),
            random.choice(["系统稳定性", "业务连续性"])
        )
    else:
        return template.format(
            random.choice(["全面", "专业", "定制化"]),
            random.choice(["咨询", "实施", "运维"]),
            random.choice(["满足需求", "降低成本", "提高效率"]),
            project_name,
            random.choice(["灵活", "透明", "公正"]),
            random.choice(["按时交付", "质量保证"])
        )

def generate_slices():
    slices = []
    section_idx = 0
    
    for proj_idx, project in enumerate(PROJECTS, 1):
        project_name = project["name"]
        
        for label, sections_list in [("技术", TECH_SECTIONS), ("商务", COMMERCIAL_SECTIONS), ("其他", OTHER_SECTIONS)]:
            num_sections = random.randint(3, 8) if label == "技术" else random.randint(2, 5)
            selected_sections = random.sample(sections_list, num_sections)
            
            for level, section_title in enumerate(selected_sections, 1):
                section_idx += 1
                text_preview = generate_text_preview(project_name, section_title, label)
                text_length = len(text_preview) + random.randint(50, 200)
                para_count = random.randint(2, 8)
                
                slice_data = {
                    "project_dir": project_name,
                    "project_idx": proj_idx,
                    "docx_file": f"{project_name}_{label}.docx",
                    "docx_label": label,
                    "section_idx": section_idx,
                    "level": level,
                    "title": section_title,
                    "text_length": text_length,
                    "text_preview": text_preview,
                    "para_count": para_count,
                }
                slices.append(slice_data)
    
    return slices

def generate_sql_insert(slices):
    sql_lines = []
    sql_lines.append("INSERT INTO bid_case_slice (project_dir, project_idx, docx_file, docx_label, section_idx, level, title, text_preview, text_length, para_count) VALUES")
    
    for i, s in enumerate(slices):
        title = s["title"].replace("'", "''")
        text_preview = s["text_preview"].replace("'", "''")
        line = f"""
    ('{s["project_dir"]}', {s["project_idx"]}, '{s["docx_file"]}', '{s["docx_label"]}', {s["section_idx"]}, {s["level"]}, '{title}', '{text_preview}', {s["text_length"]}, {s["para_count"]})"""
        if i < len(slices) - 1:
            line += ","
        sql_lines.append(line)
    
    sql_lines.append(";")
    return "\n".join(sql_lines)

if __name__ == "__main__":
    slices = generate_slices()
    print(f"Generated {len(slices)} case slices")
    
    sql = generate_sql_insert(slices)
    with open("/tmp/case_slices_bulk.sql", "w", encoding="utf-8") as f:
        f.write(sql)
    print("SQL file saved to /tmp/case_slices_bulk.sql")
    
    project_counts = {}
    for s in slices:
        project_counts[s["project_dir"]] = project_counts.get(s["project_dir"], 0) + 1
    
    print("\nProject distribution:")
    for proj, count in sorted(project_counts.items(), key=lambda x: -x[1]):
        print(f"  {proj}: {count} slices")
    
    print(f"\nTotal projects: {len(project_counts)}")
    print(f"Total slices: {len(slices)}")