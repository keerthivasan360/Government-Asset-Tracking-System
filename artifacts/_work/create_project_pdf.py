from pathlib import Path
from xml.sax.saxutils import escape
from docx import Document
from docx.table import Table as WordTable
from docx.text.paragraph import Paragraph as WordParagraph
from docx.oxml.ns import qn
from reportlab.platypus import SimpleDocTemplate, Paragraph, Table, TableStyle, PageBreak, Image, Spacer, Preformatted
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.enums import TA_LEFT, TA_RIGHT
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from pypdf import PdfReader
from pdf2image import convert_from_path
import json

ROOT=Path(__file__).resolve().parents[2]
WORK=ROOT/'artifacts/_work'
OUT=ROOT/'output/pdf';OUT.mkdir(parents=True,exist_ok=True)
for name,file in [('Arial','arial.ttf'),('Arial-Bold','arialbd.ttf'),('Arial-Italic','ariali.ttf'),('Consolas','consola.ttf')]:
    pdfmetrics.registerFont(TTFont(name,'C:/Windows/Fonts/'+file))
pdfmetrics.registerFontFamily('Arial',normal='Arial',bold='Arial-Bold',italic='Arial-Italic',boldItalic='Arial-Bold')
normal=ParagraphStyle('Body',fontName='Arial',fontSize=10,leading=13.2,spaceAfter=7,textColor=colors.black)
styles={
    'Normal':normal,
    'Title':ParagraphStyle('Title',parent=normal,fontName='Arial-Bold',fontSize=26,leading=30,spaceAfter=12),
    'Subtitle':ParagraphStyle('Subtitle',parent=normal,fontSize=13,leading=18,spaceAfter=14),
    'Heading 1':ParagraphStyle('Heading1',parent=normal,fontName='Arial-Bold',fontSize=18,leading=23,spaceAfter=14,keepWithNext=True),
    'Heading 2':ParagraphStyle('Heading2',parent=normal,fontName='Arial-Bold',fontSize=12,leading=16,spaceBefore=9,spaceAfter=5,keepWithNext=True),
    'Caption':ParagraphStyle('Caption',parent=normal,fontName='Arial-Italic',fontSize=8.5,leading=11,spaceAfter=9),
    'Code':ParagraphStyle('Code',parent=normal,fontName='Consolas',fontSize=8.8,leading=11,spaceAfter=2),
    'Cell':ParagraphStyle('Cell',parent=normal,fontSize=8.5,leading=10.8,spaceAfter=0),
    'Header':ParagraphStyle('Header',parent=normal,fontName='Arial-Bold',fontSize=8.6,leading=11,textColor=colors.white,spaceAfter=0),
    'List Bullet':ParagraphStyle('Bullet',parent=normal,leftIndent=10,firstLineIndent=-8,spaceAfter=5)
}
source=Document(ROOT/'artifacts/Government_Asset_Tracking_System_Project_Documentation.docx')
story=[]
for block in source.iter_inner_content():
    if isinstance(block,WordParagraph):
        if block._p.xpath('.//w:br[@w:type="page"]'):
            story.append(PageBreak());continue
        if block._p.xpath('.//a:blip'):
            story.append(Image(str(WORK/'architecture.png'),width=491,height=199));continue
        text=block.text
        if not text.strip(): continue
        if block.runs and block.runs[0].font.name=='Consolas':
            story.append(Preformatted(text,styles['Code']));continue
        st=styles.get(block.style.name,normal)
        if block.style.name=='List Bullet': text='- '+text
        safe=escape(text)
        if block.runs and block.runs[0].bold and block.style.name=='Normal': safe='<b>'+safe+'</b>'
        story.append(Paragraph(safe,st))
    elif isinstance(block,WordTable):
        widths=[col.width.inches*72 for col in block.columns]
        scale=491/sum(widths);widths=[w*scale for w in widths]
        cells=[]
        for i,row in enumerate(block.rows):
            cells.append([Paragraph(escape(c.text),styles['Header' if i==0 else 'Cell']) for c in row.cells])
        t=Table(cells,colWidths=widths,repeatRows=1,hAlign='LEFT')
        t.setStyle(TableStyle([
            ('BACKGROUND',(0,0),(-1,0),colors.HexColor('#243747')),
            ('ROWBACKGROUNDS',(0,1),(-1,-1),[colors.HexColor('#f1f4f6'),colors.white]),
            ('GRID',(0,0),(-1,-1),.45,colors.HexColor('#d9d9d9')),
            ('VALIGN',(0,0),(-1,-1),'MIDDLE'),
            ('LEFTPADDING',(0,0),(-1,-1),7),('RIGHTPADDING',(0,0),(-1,-1),7),
            ('TOPPADDING',(0,0),(-1,-1),6),('BOTTOMPADDING',(0,0),(-1,-1),6)
        ]));story.extend([t,Spacer(1,8)])

class Report(SimpleDocTemplate):
    def afterFlowable(self,flowable):
        if isinstance(flowable,Paragraph) and flowable.style.name in ['Heading1','Title']:
            key='section-'+str(self.page)+'-'+str(len(flowable.getPlainText()))
            self.canv.bookmarkPage(key)
            self.canv.addOutlineEntry(flowable.getPlainText(),key,level=0,closed=False)

def footer(canvas,doc):
    canvas.setFont('Arial',7.5);canvas.setFillColor(colors.HexColor('#666666'))
    canvas.drawRightString(A4[0]-51,25,f'AssetGuard  |  Project documentation  |  {doc.page}')

dest=OUT/'Government_Asset_Tracking_System_Project_Documentation.pdf'
report=Report(str(dest),pagesize=A4,rightMargin=51,leftMargin=51,topMargin=43,bottomMargin=43,
    title='Government Asset Tracking System Project Documentation',author='AssetGuard Project')
report.build(story,onFirstPage=footer,onLaterPages=footer)
pdf=PdfReader(dest)
render=WORK/'pdf_review';render.mkdir(exist_ok=True)
stats=[]
for i,page in enumerate(pdf.pages):
    images=convert_from_path(str(dest),dpi=105,first_page=i+1,last_page=i+1,poppler_path='C:/Users/keert/.cache/codex-runtimes/codex-primary-runtime/dependencies/native/poppler/Library/bin')
    images[0].save(render/f'page-{i+1:02}.png')
    text=page.extract_text()
    stats.append({'page':i+1,'words':len(text.split()),'first_line':text.splitlines()[0] if text else ''})
(WORK/'pdf_page_review.json').write_text(json.dumps(stats,indent=2))
print(json.dumps({'output':str(dest),'pages':len(pdf.pages),'words':sum(s['words'] for s in stats),'page_stats':stats},indent=2))
# Contact sheets support a complete page scan before full resolution review.
from PIL import Image as PILImage,ImageDraw
for offset in range(0,len(pdf.pages),6):
    sheet=PILImage.new('RGB',(1260,1280),'#d7dce0')
    draw=ImageDraw.Draw(sheet)
    for j in range(min(6,len(pdf.pages)-offset)):
        image=PILImage.open(render/f'page-{offset+j+1:02}.png');image.thumbnail((400,600))
        x=(j%3)*420+10;y=(j//3)*640+28
        sheet.paste(image,(x,y));draw.text((x,y-19),f'Page {offset+j+1}',fill='black')
    sheet.save(render/f'sheet-{offset//6+1}.png')
