export const APP_VERSION = 'v1.43.278';

export const APP_LANGUAGES = [
  { code: 'TR', htmlLang: 'tr', label: 'Türkçe' },
  { code: 'EN', htmlLang: 'en', label: 'English' },
  { code: 'DE', htmlLang: 'de', label: 'Deutsch' },
  { code: 'FR', htmlLang: 'fr', label: 'Français' },
  { code: 'ES', htmlLang: 'es', label: 'Español' },
  { code: 'ZH', htmlLang: 'zh', label: '中文' }
];

export function localizedText(lang, tr, en, de = en, fr = en, es = en, zh = en) {
  switch (lang) {
    case 'TR': return tr;
    case 'DE': return de;
    case 'FR': return fr;
    case 'ES': return es;
    case 'ZH': return zh;
    default: return en;
  }
}

export const GENDER_PREFS = [
  { id: 'MALE', tr: 'Erkek', en: 'Men', de: 'Herren', fr: 'Homme', es: 'Hombre', zh: '男装' },
  { id: 'FEMALE', tr: 'Kadın', en: 'Women', de: 'Damen', fr: 'Femme', es: 'Mujer', zh: '女装' },
  { id: 'UNISEX', tr: 'Unisex', en: 'Unisex', de: 'Unisex', fr: 'Unisex', es: 'Unisex', zh: '中性/百搭' }
];

export const THEME_MODES = [
  {
    id: 'MODERN',
    tr: 'Modern (Açık)', en: 'Modern (Light)', de: 'Modern (Hell)', fr: 'Moderne (Clair)', es: 'Moderno (Claro)', zh: '现代 (浅色)',
    subTr: 'Ferah krem zemin, beyaz kartlar ve sade katalog görünümü',
    subEn: 'Airy cream canvas, crisp white cards and minimal catalog aesthetic',
    subDe: 'Heller Cremeton, weiße Karten und minimalistische Katalogoptik',
    subFr: 'Fond crème lumineux, cartes blanches épurées et style catalogue',
    subEs: 'Fondo crema luminoso, tarjetas blancas limpias y estética minimalista',
    subZh: '柔和米白画布、纯白卡片与极简目录美学'
  },
  {
    id: 'MODERN_DARK',
    tr: 'Modern (Koyu)', en: 'Modern (Dark)', de: 'Modern (Dunkel)', fr: 'Moderne (Sombre)', es: 'Moderno (Oscuro)', zh: '现代 (深色)',
    subTr: 'Koyu oniks zemin, sıcak taş kartlar ve terracotta vurgular',
    subEn: 'Deep onyx canvas, warm stone cards and terracotta accents',
    subDe: 'Tiefer Onyx-Hintergrund, warme Steinkarten und Terrakotta-Akzente',
    subFr: 'Fond onyx profond, cartes pierre chaude et accents terracotta',
    subEs: 'Fondo ónix profundo, tarjetas piedra cálida y acentos terracota',
    subZh: '深邃玛瑙黑背景、暖石卡片与赤陶点缀'
  },
  {
    id: 'STREET',
    tr: 'Sokak Tarzı', en: 'Street Style', de: 'Streetstyle', fr: 'Street Style', es: 'Estilo Urbano', zh: '街头潮流',
    subTr: 'Koyu arduvaz zemin, grafit kartlar ve sıcak kehribar sokak vurguları',
    subEn: 'Deep slate dark canvas, graphite cards and warm amber street accents',
    subDe: 'Schieferdunkler Hintergrund, Graphitkarten und warme Bernstein-Akzente',
    subFr: 'Fond ardoise sombre, cartes graphite et accents ambre urbains',
    subEs: 'Fondo pizarra oscuro, tarjetas grafito y acentos ámbar urbanos',
    subZh: '深岩灰背景、石墨卡片与暖琥珀街头感点缀'
  }
];

export const CATEGORIES = [
  { id: 'TOP', tr: 'Üst Giyim', en: 'Tops', de: 'Oberteile', fr: 'Hauts', es: 'Partes de arriba', zh: '上装' },
  { id: 'BOTTOM', tr: 'Alt Giyim', en: 'Bottoms', de: 'Hosen & Röcke', fr: 'Bas', es: 'Partes de abajo', zh: '下装' },
  { id: 'OUTERWEAR', tr: 'Dış Giyim', en: 'Outerwear', de: 'Jacken & Mäntel', fr: 'Manteaux & Vestes', es: 'Abrigos y Chaquetas', zh: '外套' },
  { id: 'SHOES', tr: 'Ayakkabı', en: 'Shoes', de: 'Schuhe', fr: 'Chaussures', es: 'Calzado', zh: '鞋履' },
  { id: 'ACCESSORY', tr: 'Aksesuar', en: 'Accessories', de: 'Accessoires', fr: 'Accessoires', es: 'Accesorios', zh: '配饰' },
  { id: 'SUIT', tr: 'Takım & Özel', en: 'Sets & Suits', de: 'Anzüge & Kleider', fr: 'Ensembles & Robes', es: 'Conjuntos y Vestidos', zh: '套装与连衣裙' }
];

export const SUBCATEGORIES = [
  { id: 'TSHIRT', cat: 'TOP', warmth: 1, tr: 'Tişört', en: 'T-Shirt', de: 'T-Shirt', fr: 'T-shirt', es: 'Camiseta', zh: 'T恤' },
  { id: 'POLO', cat: 'TOP', warmth: 1, tr: 'Polo Yaka', en: 'Polo Shirt', de: 'Poloshirt', fr: 'Polo', es: 'Polo', zh: 'Polo衫' },
  { id: 'SHIRT', cat: 'TOP', warmth: 2, tr: 'Gömlek', en: 'Shirt', de: 'Hemd / Bluse', fr: 'Chemise', es: 'Camisa', zh: '衬衫' },
  { id: 'CROP_TOP', cat: 'TOP', warmth: 1, tr: 'Crop & Bluz', en: 'Crop & Blouse', de: 'Crop & Bluse', fr: 'Crop Top & Blouse', es: 'Crop & Blusa', zh: '短款/女衫' },
  { id: 'SWEATSHIRT', cat: 'TOP', warmth: 3, tr: 'Sweatshirt', en: 'Sweatshirt', de: 'Sweatshirt', fr: 'Sweatshirt', es: 'Sudadera', zh: '套头卫衣' },
  { id: 'HOODIE', cat: 'TOP', warmth: 4, tr: 'Hoodie', en: 'Hoodie', de: 'Hoodie', fr: 'Sweat à capuche', es: 'Sudadera con capucha', zh: '连帽卫衣' },
  { id: 'CARDIGAN', cat: 'TOP', warmth: 4, tr: 'Hırka', en: 'Cardigan', de: 'Strickjacke', fr: 'Gilet', es: 'Cárdigan', zh: '针织开衫' },
  { id: 'SWEATER', cat: 'TOP', warmth: 5, tr: 'Kazak & Triko', en: 'Sweater & Knit', de: 'Pullover', fr: 'Pull', es: 'Suéter', zh: '毛衣/针织' },
  { id: 'TANK', cat: 'TOP', warmth: 1, tr: 'Atlet', en: 'Tank Top', de: 'Top', fr: 'Débardeur', es: 'Camiseta sin mangas', zh: '背心' },

  { id: 'PANTS', cat: 'BOTTOM', warmth: 3, tr: 'Pantolon', en: 'Trousers', de: 'Stoffhose', fr: 'Pantalon', es: 'Pantalón', zh: '长裤' },
  { id: 'JEANS', cat: 'BOTTOM', warmth: 3, tr: 'Kot / Jean', en: 'Jeans', de: 'Jeans', fr: 'Jean', es: 'Vaqueros', zh: '牛仔裤' },
  { id: 'CHINO', cat: 'BOTTOM', warmth: 3, tr: 'Chino / Kumaş', en: 'Chinos', de: 'Chinohose', fr: 'Chino', es: 'Chino', zh: '休闲西裤' },
  { id: 'CARGO', cat: 'BOTTOM', warmth: 3, tr: 'Kargo Pantolon', en: 'Cargo Pants', de: 'Cargohose', fr: 'Pantalon cargo', es: 'Pantalón cargo', zh: '工装裤' },
  { id: 'SWEATPANTS', cat: 'BOTTOM', warmth: 3, tr: 'Eşofman Altı', en: 'Sweatpants', de: 'Jogginghose', fr: 'Jogging', es: 'Pantalón deportivo', zh: '运动裤' },
  { id: 'SHORTS', cat: 'BOTTOM', warmth: 1, tr: 'Şort', en: 'Shorts', de: 'Shorts', fr: 'Short', es: 'Pantalón corto', zh: '短裤' },
  { id: 'SKIRT', cat: 'BOTTOM', warmth: 2, tr: 'Etek', en: 'Skirt', de: 'Rock', fr: 'Jupe', es: 'Falda', zh: '半身裙' },

  { id: 'JACKET', cat: 'OUTERWEAR', warmth: 4, tr: 'Ceket', en: 'Jacket', de: 'Jacke', fr: 'Veste', es: 'Chaqueta', zh: '夹克' },
  { id: 'COAT', cat: 'OUTERWEAR', warmth: 5, tr: 'Mont & Kaban', en: 'Coat & Puffer', de: 'Mantel & Daunenjacke', fr: 'Manteau & Doudoune', es: 'Abrigo', zh: '大衣/羽绒服' },
  { id: 'BLAZER', cat: 'OUTERWEAR', warmth: 3, tr: 'Blazer Ceket', en: 'Blazer', de: 'Blazer', fr: 'Blazer', es: 'Blazer', zh: '西装外套' },
  { id: 'TRENCH', cat: 'OUTERWEAR', warmth: 4, tr: 'Trençkot', en: 'Trench Coat', de: 'Trenchcoat', fr: 'Trench', es: 'Gabardina', zh: '风衣' },
  { id: 'VEST', cat: 'OUTERWEAR', warmth: 4, tr: 'Şişme Yelek', en: 'Puffer Vest', de: 'Steppweste', fr: 'Doudoune sans manches', es: 'Chaleco', zh: '马甲' },

  { id: 'SNEAKER', cat: 'SHOES', warmth: 2, tr: 'Sneaker', en: 'Sneakers', de: 'Sneaker', fr: 'Baskets', es: 'Zapatillas', zh: '运动鞋' },
  { id: 'BOOT', cat: 'SHOES', warmth: 5, tr: 'Bot', en: 'Boots', de: 'Stiefel', fr: 'Bottines', es: 'Botas', zh: '靴子' },
  { id: 'LOAFER', cat: 'SHOES', warmth: 2, tr: 'Klasik & Loafer', en: 'Loafers', de: 'Loafer', fr: 'Mocassins', es: 'Mocasines', zh: '乐福/皮鞋' },
  { id: 'HEELS', cat: 'SHOES', warmth: 2, tr: 'Topuklu Ayakkabı', en: 'Heels', de: 'Absatzschuhe', fr: 'Talons', es: 'Tacones', zh: '高跟鞋' },
  { id: 'SLIPPER', cat: 'SHOES', warmth: 1, tr: 'Terlik & Sandalet', en: 'Slides & Sandals', de: 'Sandalen', fr: 'Sandales', es: 'Sandalias', zh: '凉拖/凉鞋' },

  { id: 'WATCH', cat: 'ACCESSORY', warmth: 3, tr: 'Saat', en: 'Watch', de: 'Uhr', fr: 'Montre', es: 'Reloj', zh: '手表' },
  { id: 'CAP', cat: 'ACCESSORY', warmth: 3, tr: 'Şapka & Bere', en: 'Cap & Beanie', de: 'Mütze & Cap', fr: 'Casquette & Bonnet', es: 'Gorra y Gorro', zh: '帽子' },
  { id: 'BAG', cat: 'ACCESSORY', warmth: 3, tr: 'Çanta', en: 'Bag', de: 'Tasche', fr: 'Sac', es: 'Bolso', zh: '包包' },
  { id: 'GLASSES', cat: 'ACCESSORY', warmth: 1, tr: 'Gözlük', en: 'Sunglasses', de: 'Sonnenbrille', fr: 'Lunettes', es: 'Gafas', zh: '墨镜' },
  { id: 'BELT', cat: 'ACCESSORY', warmth: 3, tr: 'Kemer', en: 'Belt', de: 'Gürtel', fr: 'Ceinture', es: 'Cinturón', zh: '腰带' },
  { id: 'SCARF', cat: 'ACCESSORY', warmth: 5, tr: 'Atkı', en: 'Scarf', de: 'Schal', fr: 'Écharpe', es: 'Bufanda', zh: '围巾' },

  { id: 'SUIT_SET', cat: 'SUIT', warmth: 3, tr: 'Takım Elbise', en: 'Suit', de: 'Anzug', fr: 'Costume', es: 'Traje', zh: '西服套装' },
  { id: 'DRESS', cat: 'SUIT', warmth: 2, tr: 'Elbise', en: 'Dress', de: 'Kleid', fr: 'Robe', es: 'Vestido', zh: '连衣裙' },
  { id: 'TRACKSUIT', cat: 'SUIT', warmth: 3, tr: 'Eşofman Takımı', en: 'Tracksuit', de: 'Trainingsanzug', fr: 'Survêtement', es: 'Chándal', zh: '运动套装' }
];

export const STYLES = [
  { id: 'CASUAL', tr: 'Günlük', en: 'Casual', de: 'Lässig', fr: 'Décontracté', es: 'Casual', zh: '日常休闲' },
  { id: 'SPORT', tr: 'Spor & Rahat', en: 'Sporty', de: 'Sportlich', fr: 'Sport', es: 'Deportivo', zh: '运动潮流' },
  { id: 'SMART', tr: 'Smart Casual', en: 'Smart Casual', de: 'Smart Casual', fr: 'Chic décontracté', es: 'Smart Casual', zh: '轻熟商务' },
  { id: 'FORMAL', tr: 'Klasik / Ofis', en: 'Formal', de: 'Formell', fr: 'Formel / Bureau', es: 'Formal', zh: '正式通勤' }
];

export const SEASONS = [
  { id: 'ALL', tr: '4 Mevsim', en: 'All Seasons', de: 'Alle Jahreszeiten', fr: 'Toutes saisons', es: 'Todo el año', zh: '四季通用' },
  { id: 'SPRING', tr: 'İlkbahar', en: 'Spring', de: 'Frühling', fr: 'Printemps', es: 'Primavera', zh: '春季' },
  { id: 'SUMMER', tr: 'Yaz', en: 'Summer', de: 'Sommer', fr: 'Été', es: 'Verano', zh: '夏季' },
  { id: 'AUTUMN', tr: 'Sonbahar', en: 'Autumn', de: 'Herbst', fr: 'Automne', es: 'Otoño', zh: '秋季' },
  { id: 'WINTER', tr: 'Kış', en: 'Winter', de: 'Winter', fr: 'Hiver', es: 'Invierno', zh: '冬季' }
];

export const GARMENT_COLORS = [
  { id: 'WHITE', hex: '#FAFAFA', isNeutral: true, hueGroup: 'NEUTRAL_LIGHT', tr: 'Beyaz', en: 'White', de: 'Weiß', fr: 'Blanc', es: 'Blanco', zh: '白色' },
  { id: 'ECRU', hex: '#F3EFE4', isNeutral: true, hueGroup: 'NEUTRAL_LIGHT', tr: 'Ekru / Krem', en: 'Ecru / Cream', de: 'Ecru / Creme', fr: 'Écru / Crème', es: 'Crudo / Crema', zh: '米白/奶白' },
  { id: 'BEIGE', hex: '#DCCBB2', isNeutral: true, hueGroup: 'EARTH', tr: 'Bej', en: 'Beige', de: 'Beige', fr: 'Beige', es: 'Beige', zh: '米色' },
  { id: 'STONE', hex: '#C2B5A3', isNeutral: true, hueGroup: 'EARTH', tr: 'Taş / Vizon', en: 'Stone', de: 'Steingrau', fr: 'Taupe / Pierre', es: 'Piedra / Topo', zh: '石灰色' },
  { id: 'CAMEL', hex: '#B88B58', isNeutral: true, hueGroup: 'EARTH', tr: 'Camel', en: 'Camel', de: 'Camel', fr: 'Camel', es: 'Camel', zh: '驼色' },
  { id: 'BROWN', hex: '#5E3B24', isNeutral: true, hueGroup: 'EARTH', tr: 'Kahverengi', en: 'Brown', de: 'Braun', fr: 'Marron', es: 'Marrón', zh: '棕色' },
  { id: 'LIGHT_GRAY', hex: '#D0D1CE', isNeutral: true, hueGroup: 'NEUTRAL_LIGHT', tr: 'Açık Gri', en: 'Light Gray', de: 'Hellgrau', fr: 'Gris clair', es: 'Gris claro', zh: '浅灰' },
  { id: 'GRAY', hex: '#8E908C', isNeutral: true, hueGroup: 'NEUTRAL_LIGHT', tr: 'Gri', en: 'Gray', de: 'Grau', fr: 'Gris', es: 'Gris', zh: '灰色' },
  { id: 'CHARCOAL', hex: '#434542', isNeutral: true, hueGroup: 'NEUTRAL_DARK', tr: 'Antrasit', en: 'Charcoal', de: 'Anthrazit', fr: 'Anthracite', es: 'Antracita', zh: '炭灰' },
  { id: 'BLACK', hex: '#1A1A19', isNeutral: true, hueGroup: 'NEUTRAL_DARK', tr: 'Siyah', en: 'Black', de: 'Schwarz', fr: 'Noir', es: 'Negro', zh: '黑色' },
  { id: 'NAVY', hex: '#1B2A44', isNeutral: true, hueGroup: 'BLUE', tr: 'Lacivert', en: 'Navy', de: 'Marineblau', fr: 'Bleu marine', es: 'Azul marino', zh: '藏青' },

  { id: 'DENIM', hex: '#4A6E8E', isNeutral: false, hueGroup: 'BLUE', tr: 'Denim Mavi', en: 'Denim Blue', de: 'Denimblau', fr: 'Bleu denim', es: 'Azul vaquero', zh: '牛仔蓝' },
  { id: 'SKY', hex: '#8FB8D8', isNeutral: false, hueGroup: 'BLUE', tr: 'Açık Mavi', en: 'Sky Blue', de: 'Hellblau', fr: 'Bleu ciel', es: 'Azul cielo', zh: '天蓝' },
  { id: 'COBALT', hex: '#2152A3', isNeutral: false, hueGroup: 'BLUE', tr: 'Saks Mavi', en: 'Cobalt Blue', de: 'Kobaltblau', fr: 'Bleu cobalt', es: 'Azul cobalto', zh: '钴蓝' },
  { id: 'TURQUOISE', hex: '#3A9B9C', isNeutral: false, hueGroup: 'COOL_ACCENT', tr: 'Turkuaz', en: 'Turquoise', de: 'Türkis', fr: 'Turquoise', es: 'Turquesa', zh: '青绿/松石' },
  { id: 'MINT', hex: '#98C9B5', isNeutral: false, hueGroup: 'GREEN', tr: 'Mint Yeşili', en: 'Mint', de: 'Mintgrün', fr: 'Menthe', es: 'Menta', zh: '薄荷绿' },
  { id: 'SAGE', hex: '#839680', isNeutral: false, hueGroup: 'GREEN', tr: 'Adaçayı', en: 'Sage', de: 'Salbeigrün', fr: 'Sauge', es: 'Salvia', zh: '鼠尾草绿' },
  { id: 'GREEN', hex: '#2E6F44', isNeutral: false, hueGroup: 'GREEN', tr: 'Yeşil', en: 'Green', de: 'Grün', fr: 'Vert', es: 'Verde', zh: '绿色' },
  { id: 'OLIVE', hex: '#556042', isNeutral: false, hueGroup: 'GREEN', tr: 'Haki / Zeytin', en: 'Olive / Khaki', de: 'Oliv / Khaki', fr: 'Kaki / Olive', es: 'Oliva / Caqui', zh: '军绿/橄榄绿' },
  { id: 'YELLOW', hex: '#EAC435', isNeutral: false, hueGroup: 'WARM_ACCENT', tr: 'Sarı', en: 'Yellow', de: 'Gelb', fr: 'Jaune', es: 'Amarillo', zh: '黄色' },
  { id: 'MUSTARD', hex: '#C8922B', isNeutral: false, hueGroup: 'WARM_ACCENT', tr: 'Hardal', en: 'Mustard', de: 'Senfgelb', fr: 'Moutarde', es: 'Mostaza', zh: '芥末黄' },
  { id: 'ORANGE', hex: '#D96B38', isNeutral: false, hueGroup: 'WARM_ACCENT', tr: 'Turuncu', en: 'Orange', de: 'Orange', fr: 'Orange', es: 'Naranja', zh: '橙色' },
  { id: 'TERRACOTTA', hex: '#B6533C', isNeutral: false, hueGroup: 'WARM_ACCENT', tr: 'Kiremit', en: 'Terracotta', de: 'Terrakotta', fr: 'Terracotta', es: 'Terracota', zh: '赤陶橘' },
  { id: 'RED', hex: '#C23232', isNeutral: false, hueGroup: 'WARM_ACCENT', tr: 'Kırmızı', en: 'Red', de: 'Rot', fr: 'Rouge', es: 'Rojo', zh: '红色' },
  { id: 'BURGUNDY', hex: '#6B212A', isNeutral: false, hueGroup: 'WARM_ACCENT', tr: 'Bordo', en: 'Burgundy', de: 'Bordeaux', fr: 'Bordeaux', es: 'Burdeos', zh: '酒红' },
  { id: 'BLUSH', hex: '#E8CCD7', isNeutral: false, hueGroup: 'NEUTRAL_LIGHT', tr: 'Pudra', en: 'Blush', de: 'Puderrosa', fr: 'Rose poudré', es: 'Rosa empolvado', zh: '裸粉' },
  { id: 'PINK', hex: '#E07A9A', isNeutral: false, hueGroup: 'WARM_ACCENT', tr: 'Pembe', en: 'Pink', de: 'Rosa', fr: 'Rose', es: 'Rosa', zh: '粉色' },
  { id: 'PURPLE', hex: '#6E5282', isNeutral: false, hueGroup: 'COOL_ACCENT', tr: 'Mor / Lila', en: 'Purple', de: 'Lila', fr: 'Violet', es: 'Morado', zh: '紫色' },

  { id: 'CHAMPAGNE', hex: '#F2E6D0', isNeutral: true, hueGroup: 'NEUTRAL_LIGHT', tr: 'Şampanya', en: 'Champagne', de: 'Champagner', fr: 'Champagne', es: 'Champán', zh: '香槟色' },
  { id: 'SAND', hex: '#CDB99C', isNeutral: true, hueGroup: 'EARTH', tr: 'Kum Beji', en: 'Sand', de: 'Sand', fr: 'Sable', es: 'Arena', zh: '沙色' },
  { id: 'CHOCOLATE', hex: '#3E261A', isNeutral: false, hueGroup: 'EARTH', tr: 'Çikolata Kahve', en: 'Chocolate', de: 'Schokolade', fr: 'Chocolat', es: 'Chocolate', zh: '巧克力棕' },
  { id: 'SLATE_BLUE', hex: '#53687E', isNeutral: false, hueGroup: 'BLUE', tr: 'Arduvaz Mavi', en: 'Slate Blue', de: 'Schieferblau', fr: 'Bleu ardoise', es: 'Azul pizarra', zh: '板岩蓝' },
  { id: 'ICE_BLUE', hex: '#B8D4E8', isNeutral: false, hueGroup: 'BLUE', tr: 'Buz Mavisi', en: 'Ice Blue', de: 'Eisblau', fr: 'Bleu glacier', es: 'Azul hielo', zh: '冰蓝' },
  { id: 'PETROL', hex: '#19535F', isNeutral: false, hueGroup: 'COOL_ACCENT', tr: 'Petrol Yeşili', en: 'Petrol', de: 'Petrol', fr: 'Bleu pétrole', es: 'Petróleo', zh: '孔雀蓝' },
  { id: 'TEAL', hex: '#2A7B7C', isNeutral: false, hueGroup: 'COOL_ACCENT', tr: 'Camgöbeği / Teal', en: 'Teal', de: 'Blaugrün', fr: 'Sarcelle', es: 'Verde azulado', zh: '水鸭绿' },
  { id: 'EMERALD', hex: '#1B6B4F', isNeutral: false, hueGroup: 'GREEN', tr: 'Zümrüt Yeşili', en: 'Emerald', de: 'Smaragdgrün', fr: 'Émeraude', es: 'Esmeralda', zh: '祖母绿' },
  { id: 'FOREST', hex: '#214231', isNeutral: false, hueGroup: 'GREEN', tr: 'Orman Yeşili', en: 'Forest Green', de: 'Waldgrün', fr: 'Vert forêt', es: 'Verde bosque', zh: '森林绿' },
  { id: 'OCHRE', hex: '#C78A28', isNeutral: false, hueGroup: 'WARM_ACCENT', tr: 'Safran / Ochre', en: 'Ochre', de: 'Ocker', fr: 'Ocre', es: 'Ocre', zh: '赭石黄' },
  { id: 'RUST', hex: '#A4452B', isNeutral: false, hueGroup: 'WARM_ACCENT', tr: 'Pas Turuncu', en: 'Rust', de: 'Rostrot', fr: 'Rouille', es: 'Óxido', zh: '铁锈红' },
  { id: 'CORAL', hex: '#E36B5B', isNeutral: false, hueGroup: 'WARM_ACCENT', tr: 'Mercan', en: 'Coral', de: 'Koralle', fr: 'Corail', es: 'Coral', zh: '珊瑚红' },
  { id: 'WINE', hex: '#5A1A28', isNeutral: false, hueGroup: 'WARM_ACCENT', tr: 'Şarap Bordo', en: 'Wine Red', de: 'Weinrot', fr: 'Lie-de-vin', es: 'Vino', zh: '深红酒' },
  { id: 'ROSEWOOD', hex: '#B36A72', isNeutral: false, hueGroup: 'WARM_ACCENT', tr: 'Gül Kurusu', en: 'Rosewood', de: 'Rosenholz', fr: 'Bois de rose', es: 'Palo de rosa', zh: '玫瑰木' },
  { id: 'LAVENDER', hex: '#B7A6D6', isNeutral: false, hueGroup: 'COOL_ACCENT', tr: 'Lavanta', en: 'Lavender', de: 'Lavendel', fr: 'Lavande', es: 'Lavanda', zh: '薰衣草紫' },
  { id: 'PLUM', hex: '#4E2A4F', isNeutral: false, hueGroup: 'COOL_ACCENT', tr: 'Mürdüm', en: 'Plum', de: 'Pflaume', fr: 'Prune', es: 'Ciruela', zh: '梅子紫' }
];

export const WEATHER_CONDITIONS = {
  HOT: { id: 'HOT', emoji: '☀️', sampleTemp: 27, preferredSeason: 'SUMMER', tr: 'Güneşli & Sıcak', en: 'Sunny & Hot', zh: '晴热高温', trHint: 'İnce ve nefes alan parçalar', enHint: 'Light & breathable pieces', zhHint: '轻薄透气的单品' },
  WARM: { id: 'WARM', emoji: '🌤️', sampleTemp: 21, preferredSeason: 'SPRING', tr: 'Ilık & Açık', en: 'Warm & Mild', zh: '温暖晴朗', trHint: 'Tişört, polo veya hafif gömlek', enHint: 'T-shirt, polo or light shirt', zhHint: 'T恤、Polo衫或轻薄衬衫' },
  COOL: { id: 'COOL', emoji: '🌥️', sampleTemp: 14, preferredSeason: 'AUTUMN', tr: 'Serin', en: 'Cool', zh: '微凉', trHint: 'Hoodie, sweatshirt, hırka veya ceket', enHint: 'Hoodie, sweatshirt, cardigan or jacket', zhHint: '卫衣、开衫或轻薄夹克' },
  RAINY: { id: 'RAINY', emoji: '🌧️', sampleTemp: 12, preferredSeason: 'AUTUMN', tr: 'Yağmurlu & Rüzgarlı', en: 'Rainy & Windy', zh: '阴雨多风', trHint: 'Koruyucu ceket/trençkot ve kapalı ayakkabı', enHint: 'Protective jacket/trench & closed shoes', zhHint: '防风夹克/风衣与防水鞋履' },
  COLD: { id: 'COLD', emoji: '❄️', sampleTemp: 5, preferredSeason: 'WINTER', tr: 'Soğuk', en: 'Cold', zh: '寒冷', trHint: 'Kazak, mont/kaban ve sıcak katmanlar', enHint: 'Sweater, coat & warm layers', zhHint: '毛衣、大衣/羽绒服与保暖内搭' }
};

export const TURKISH_CITIES = [
  { id: 'ISTANBUL', label: 'İstanbul', lat: 41.0082, lon: 28.9784 },
  { id: 'ANKARA', label: 'Ankara', lat: 39.9334, lon: 32.8597 },
  { id: 'IZMIR', label: 'İzmir', lat: 38.4192, lon: 27.1287 },
  { id: 'BURSA', label: 'Bursa', lat: 40.1885, lon: 29.0610 },
  { id: 'ANTALYA', label: 'Antalya', lat: 36.8969, lon: 30.7133 },
  { id: 'ADANA', label: 'Adana', lat: 37.0000, lon: 35.3213 },
  { id: 'KONYA', label: 'Konya', lat: 37.8746, lon: 32.4932 },
  { id: 'ESKISEHIR', label: 'Eskişehir', lat: 39.7767, lon: 30.5206 },
  { id: 'GAZIANTEP', label: 'Gaziantep', lat: 37.0662, lon: 37.3833 },
  { id: 'TRABZON', label: 'Trabzon', lat: 41.0027, lon: 39.7168 }
];

export const STRINGS = {
  TR: {
    appTitle: 'Dolabım',
    tabOutfits: 'Kombinler',
    tabSocial: 'Sosyal',
    tabInspiration: 'İlham',
    tabSummary: 'Dolap Özeti',
    dockSummary: 'Özet',
    all: 'Tümü',
    favorites: 'Favoriler',
    wishlist: 'İstek Listesi',
    searchPlaceholder: 'Gömlek, hoodie, pembe, kargo ara...',
    emptyWardrobeTitle: 'Dolabını Oluştur',
    emptyWardrobeBody: 'Kıyafetlerini çek veya galerinden seç, kırp, renk ve tür tahminini al. İstersen arka planı kaldır ya da olduğu gibi ekle.',
    takePhotoAdd: 'Fotoğraf Çek ve Ekle',
    pickFromGallery: 'Galeriden Seç',
    noFilterMatch: 'Bu filtreye uygun parça yok',
    clearFilters: 'Filtreleri Temizle',
    settingsTitle: 'Görünüm & Ayarlar',
    themeLabel: 'Tema Seçimi',
    languageLabel: 'Dil / Language',
    studioPromptBtn: 'Stüdyo Fotoğraf Promptu',
    releaseNotesBtn: 'Sürüm Notları (v1.43.278)',
    releaseNotesTitle: 'Sürüm Notları',
    copyPrompt: 'Promptu Kopyala',
    copiedMsg: 'Prompt panoya kopyalandı',
    close: 'Kapat',
    ok: 'Tamam',
    outfitsSubtitle: '7 günlük hava tahmini & renk uyumu',
    tryOn3D: '3D Kabin',
    create: 'Oluştur',
    needTwoItemsTitle: 'Kombin önerisi için en az 2 parça ekle',
    needTwoItemsBody: 'Dolabına tişört, gömlek, pantolon veya sneaker ekledikten sonra hava durumuna göre öneriler burada çıkacak.',
    suggestionsFor: 'İçin Öneriler',
    suggestDifferent: 'Farklı Öner',
    try3DShort: '3D Dene',
    wornBtn: 'Giydim',
    save: 'Kaydet',
    savedOutfitsTitle: 'Kayıtlı Kombinlerim',
    outfitDetailTitle: 'Kombin Detayı',
    topToBottomHint: 'Üstten alta doğru katmanlar · Parçaları değiştirebilir veya ekleyebilirsin',
    swapPiece: 'Değiştir',
    removePiece: 'Çıkar',
    addPiece: 'Parça Ekle',
    saveOutfit: 'Kombini Kaydet',
    inspirationSubtitle: 'Genç sokak stili · Erkek & Kadın kombinleri',
    summarySubtitle: 'Renk dengen, parça türlerin ve eksik analizi',
    missingPiecesTitle: 'Eksik Temel Parçalar',
    missingColorsTitle: 'Eksik Uyumlu Renkler',
    expand: 'Diğerlerini Gör',
    collapse: 'Daralt',
    addToWardrobeLabel: 'Dolabım',
    addToWishlistLabel: 'İstek Listesi',
    priceLabel: 'Fiyat (İstek Listesi için)',
    pricePlaceholder: 'Örn: 1.499 ₺',
    moveToWardrobeBtn: 'Satın Aldım · Dolabıma Taşı',
    movedToWardrobeMsg: 'Dolabına taşındı',
    installTitle: 'Ana Ekrana Ekle',
    installSub: "iPhone & Android'de tam ekran uygulama gibi aç"
  },
  EN: {
    appTitle: 'My Wardrobe',
    tabOutfits: 'Outfits',
    tabSocial: 'Social',
    tabInspiration: 'Lookbook',
    tabSummary: 'Summary',
    dockSummary: 'Summary',
    all: 'All',
    favorites: 'Favorites',
    wishlist: 'Wishlist',
    searchPlaceholder: 'Search shirt, hoodie, pink, cargo...',
    emptyWardrobeTitle: 'Build Your Wardrobe',
    emptyWardrobeBody: 'Snap or pick clothes from your gallery, crop them, get auto color & type prediction, and choose studio cutout or original photo.',
    takePhotoAdd: 'Take Photo & Add',
    pickFromGallery: 'Pick from Gallery',
    noFilterMatch: 'No items match this filter',
    clearFilters: 'Clear Filters',
    settingsTitle: 'Appearance & Settings',
    themeLabel: 'Theme Style',
    languageLabel: 'Language / Dil',
    studioPromptBtn: 'Studio Photo Prompt',
    releaseNotesBtn: 'Release Notes (v1.43.278)',
    releaseNotesTitle: 'Release Notes',
    copyPrompt: 'Copy Prompt',
    copiedMsg: 'Prompt copied to clipboard',
    close: 'Close',
    ok: 'Done',
    outfitsSubtitle: '7-day weather forecast & color harmony',
    tryOn3D: '3D Try-On',
    create: 'Create',
    needTwoItemsTitle: 'Add at least 2 items for outfit ideas',
    needTwoItemsBody: 'Add tops, bottoms or shoes to unlock weather-aware outfit suggestions.',
    suggestionsFor: 'Outfit Picks',
    suggestDifferent: 'Shuffle',
    try3DShort: '3D Fit',
    wornBtn: 'Wore It',
    save: 'Save',
    savedOutfitsTitle: 'Saved Outfits',
    outfitDetailTitle: 'Outfit Builder',
    topToBottomHint: 'Top-to-bottom layers · Swap, remove or add pieces live',
    swapPiece: 'Swap',
    removePiece: 'Remove',
    addPiece: 'Add Piece',
    saveOutfit: 'Save Outfit',
    inspirationSubtitle: 'Street style lookbook · Men & Women',
    summarySubtitle: 'Color balance, piece breakdown & gap insights',
    missingPiecesTitle: 'Missing Core Pieces',
    missingColorsTitle: 'Missing Harmonious Colors',
    expand: 'Show More',
    collapse: 'Collapse',
    addToWardrobeLabel: 'My Closet',
    addToWishlistLabel: 'Wishlist',
    priceLabel: 'Price (for Wishlist)',
    pricePlaceholder: 'e.g. $45 / 1.499 ₺',
    moveToWardrobeBtn: 'Bought It · Move to Closet',
    movedToWardrobeMsg: 'Moved to your closet',
    installTitle: 'Add to Home Screen',
    installSub: 'Install & launch full-screen on iPhone & Android'
  },
  DE: {
    appTitle: 'Mein Schrank', tabOutfits: 'Outfits', tabSocial: 'Sozial', tabInspiration: 'Lookbook', tabSummary: 'Übersicht', dockSummary: 'Bilanz',
    all: 'Alle', favorites: 'Favoriten', wishlist: 'Wunschliste', searchPlaceholder: 'Hemd, Hoodie, Rosa, Cargo suchen...',
    emptyWardrobeTitle: 'Kleiderschrank füllen', emptyWardrobeBody: 'Kleidung fotografieren oder aus der Galerie wählen, zuschneiden und automatisch Farbe & Typ erkennen.',
    takePhotoAdd: 'Foto aufnehmen', pickFromGallery: 'Aus Galerie wählen', noFilterMatch: 'Keine passenden Teile gefunden', clearFilters: 'Filter löschen',
    settingsTitle: 'Design & Einstellungen', themeLabel: 'Theme-Stil', languageLabel: 'Sprache / Language',
    studioPromptBtn: 'Studio-Foto-Prompt', releaseNotesBtn: 'Versionshinweise (v1.43.278)', releaseNotesTitle: 'Versionshinweise',
    copyPrompt: 'Prompt kopieren', copiedMsg: 'Prompt kopiert', close: 'Schließen', ok: 'Fertig',
    outfitsSubtitle: '7-Tage-Wetter & Farbharmonie', tryOn3D: '3D-Anprobe', create: 'Erstellen',
    needTwoItemsTitle: 'Mindestens 2 Teile hinzufügen', needTwoItemsBody: 'Füge Oberteile, Hosen oder Schuhe hinzu.',
    suggestionsFor: 'Outfit-Ideen', suggestDifferent: 'Neu mischen', try3DShort: '3D Test', wornBtn: 'Getragen', save: 'Speichern',
    savedOutfitsTitle: 'Gespeicherte Outfits', outfitDetailTitle: 'Outfit-Baukasten', topToBottomHint: 'Schichten von oben nach unten',
    swapPiece: 'Tauschen', removePiece: 'Entfernen', addPiece: 'Teil hinzufügen', saveOutfit: 'Outfit speichern',
    inspirationSubtitle: 'Streetstyle-Lookbook · Herren & Damen', summarySubtitle: 'Farbbalance & Lücken',
    missingPiecesTitle: 'Fehlende Basics', missingColorsTitle: 'Fehlende Harmoniefarben', expand: 'Mehr anzeigen', collapse: 'Weniger',
    addToWardrobeLabel: 'Mein Schrank', addToWishlistLabel: 'Wunschliste', priceLabel: 'Preis (Wunschliste)', pricePlaceholder: 'z. B. 45 €',
    moveToWardrobeBtn: 'Gekauft · In den Schrank', movedToWardrobeMsg: 'In deinen Schrank verschoben',
    installTitle: 'Zum Home-Bildschirm', installSub: 'Wie eine echte App auf iPhone & Android nutzen'
  },
  FR: {
    appTitle: 'Mon Dressing', tabOutfits: 'Tenues', tabSocial: 'Social', tabInspiration: 'Lookbook', tabSummary: 'Bilan', dockSummary: 'Bilan',
    all: 'Tout', favorites: 'Favoris', wishlist: "Liste d'envies", searchPlaceholder: 'Chemise, hoodie, rose, cargo...',
    emptyWardrobeTitle: 'Créer votre dressing', emptyWardrobeBody: 'Prenez vos vêtements en photo ou choisissez depuis la galerie avec détourage studio et détection auto.',
    takePhotoAdd: 'Prendre une photo', pickFromGallery: 'Galerie', noFilterMatch: 'Aucun article correspondant', clearFilters: 'Réinitialiser',
    settingsTitle: 'Apparence & Paramètres', themeLabel: 'Thème', languageLabel: 'Langue / Language',
    studioPromptBtn: 'Prompt Photo Studio', releaseNotesBtn: 'Notes de version (v1.43.278)', releaseNotesTitle: 'Notes de version',
    copyPrompt: 'Copier le prompt', copiedMsg: 'Prompt copié', close: 'Fermer', ok: 'Valider',
    outfitsSubtitle: 'Météo 7 jours & harmonie des couleurs', tryOn3D: 'Cabine 3D', create: 'Créer',
    needTwoItemsTitle: 'Ajoutez au moins 2 pièces', needTwoItemsBody: 'Ajoutez des hauts, bas ou chaussures.',
    suggestionsFor: 'Idées de tenues', suggestDifferent: 'Autre choix', try3DShort: 'Essai 3D', wornBtn: 'Porté', save: 'Enregistrer',
    savedOutfitsTitle: 'Tenues enregistrées', outfitDetailTitle: 'Détail de la tenue', topToBottomHint: 'De haut en bas · Remplacez ou ajoutez des pièces',
    swapPiece: 'Changer', removePiece: 'Retirer', addPiece: 'Ajouter', saveOutfit: 'Enregistrer',
    inspirationSubtitle: 'Lookbook streetwear · Homme & Femme', summarySubtitle: 'Équilibre des couleurs & pièces manquantes',
    missingPiecesTitle: 'Essentiels manquants', missingColorsTitle: 'Couleurs harmonieuses manquantes', expand: 'Voir plus', collapse: 'Réduire',
    addToWardrobeLabel: 'Dressing', addToWishlistLabel: 'Envies', priceLabel: "Prix (Liste d'envies)", pricePlaceholder: 'Ex : 45 €',
    moveToWardrobeBtn: 'Acheté · Mettre au dressing', movedToWardrobeMsg: 'Ajouté au dressing',
    installTitle: "Ajouter à l'écran d'accueil", installSub: 'Ouvrir en plein écran sur iPhone & Android'
  },
  ES: {
    appTitle: 'Mi Armario', tabOutfits: 'Outfits', tabSocial: 'Social', tabInspiration: 'Lookbook', tabSummary: 'Resumen', dockSummary: 'Resumen',
    all: 'Todo', favorites: 'Favoritos', wishlist: 'Lista de deseos', searchPlaceholder: 'Buscar camisa, hoodie, rosa, cargo...',
    emptyWardrobeTitle: 'Crea tu armario', emptyWardrobeBody: 'Fotografía tu ropa o elige de la galería, recorta y detecta colores y tipo automáticamente.',
    takePhotoAdd: 'Tomar foto', pickFromGallery: 'Galería', noFilterMatch: 'Sin prendas coincidentes', clearFilters: 'Limpiar filtros',
    settingsTitle: 'Aspecto y Ajustes', themeLabel: 'Tema', languageLabel: 'Idioma / Language',
    studioPromptBtn: 'Prompt Foto Estudio', releaseNotesBtn: 'Notas de versión (v1.43.278)', releaseNotesTitle: 'Notas de versión',
    copyPrompt: 'Copiar prompt', copiedMsg: 'Prompt copiado', close: 'Cerrar', ok: 'Listo',
    outfitsSubtitle: 'Clima 7 días y armonía de color', tryOn3D: 'Probador 3D', create: 'Crear',
    needTwoItemsTitle: 'Añade al menos 2 prendas', needTwoItemsBody: 'Añade partes de arriba, abajo o calzado.',
    suggestionsFor: 'Ideas', suggestDifferent: 'Cambiar', try3DShort: 'Ver 3D', wornBtn: 'Puesto', save: 'Guardar',
    savedOutfitsTitle: 'Outfits guardados', outfitDetailTitle: 'Editor de Outfit', topToBottomHint: 'Capas de arriba abajo',
    swapPiece: 'Cambiar', removePiece: 'Quitar', addPiece: 'Añadir prenda', saveOutfit: 'Guardar Outfit',
    inspirationSubtitle: 'Lookbook urbano · Hombre y Mujer', summarySubtitle: 'Balance de colores y prendas faltantes',
    missingPiecesTitle: 'Básicos faltantes', missingColorsTitle: 'Colores armónicos faltantes', expand: 'Ver más', collapse: 'Ocultar',
    addToWardrobeLabel: 'Mi Armario', addToWishlistLabel: 'Deseos', priceLabel: 'Precio (Lista de deseos)', pricePlaceholder: 'Ej: 45 €',
    moveToWardrobeBtn: 'Comprado · Mover al armario', movedToWardrobeMsg: 'Movido a tu armario',
    installTitle: 'Añadir a pantalla de inicio', installSub: 'Instalar como app en iPhone y Android'
  },
  ZH: {
    appTitle: '我的衣橱', tabOutfits: '穿搭', tabSocial: '社交', tabInspiration: '灵感', tabSummary: '衣橱统计', dockSummary: '统计',
    all: '全部', favorites: '收藏', wishlist: '心愿单', searchPlaceholder: '搜索衬衫、卫衣、粉色、工装裤...',
    emptyWardrobeTitle: '创建你的衣橱', emptyWardrobeBody: '拍摄衣物或从相册选择，支持裁剪、自动抠图与色系及款式识别。',
    takePhotoAdd: '拍照添加', pickFromGallery: '相册选择', noFilterMatch: '暂无符合条件的单品', clearFilters: '清除筛选',
    settingsTitle: '外观与设置', themeLabel: '主题风格', languageLabel: '语言 / Language',
    studioPromptBtn: '影棚白底图提示词', releaseNotesBtn: '更新日志 (v1.43.278)', releaseNotesTitle: '更新日志',
    copyPrompt: '复制提示词', copiedMsg: '已复制到剪贴板', close: '关闭', ok: '确定',
    outfitsSubtitle: '7天天气预报与色彩搭配', tryOn3D: '3D试衣间', create: '新建',
    needTwoItemsTitle: '至少添加 2 件衣物', needTwoItemsBody: '添加上装、下装或鞋子后，即可在此获取基于天气的智能穿搭建议。',
    suggestionsFor: '穿搭建议', suggestDifferent: '换一批', try3DShort: '3D试穿', wornBtn: '已穿', save: '保存',
    savedOutfitsTitle: '已保存的穿搭', outfitDetailTitle: '穿搭编辑器', topToBottomHint: '从上到下搭配 · 可替换或增减单品',
    swapPiece: '替换', removePiece: '移除', addPiece: '添加单品', saveOutfit: '保存穿搭',
    inspirationSubtitle: '街头风格指南 · 男装与女装', summarySubtitle: '色彩分布与衣橱缺口分析',
    missingPiecesTitle: '缺少的基础款单品', missingColorsTitle: '缺少的百搭色系', expand: '展开', collapse: '收起',
    addToWardrobeLabel: '放入衣橱', addToWishlistLabel: '心愿单', priceLabel: '价格 (心愿单)', pricePlaceholder: '例如：¥299',
    moveToWardrobeBtn: '已购买 · 移入衣橱', movedToWardrobeMsg: '已移入你的衣橱',
    installTitle: '添加到主屏幕', installSub: '在 iPhone 和 Android 上全屏运行'
  }
};

export const STUDIO_ECOMMERCE_PROMPT =
  'Edit this photo into a straight top-down e-commerce flat-lay product photograph of the exact same garment on a pure solid white (#FFFFFF) background. Keep the exact same viewing side of the garment facing the camera as shown in the original photo (back view stays back view, front view stays front view, maintaining the exact same hood, collar, seams, and construction). Keep every graphic print, illustration, artwork, pattern, embroidery, logo, and text completely faithful to the original garment in exact full size, coverage area, placement, proportions, colors, and intricate details. Keep the exact fabric color, tone, weave texture, ribbing, cuffs, hem, and cut unchanged. Completely remove the entire surrounding background, furniture, floor, objects, and shadows so only pure solid white (#FFFFFF) remains. Flatten camera perspective and gently smooth heavy creases while neatly laying the garment flat and symmetrical with both sleeves extended evenly at natural downward angles, lit by soft even commercial studio lighting. Center the garment large in a 1:1 square frame filling about 85% of the canvas with balanced white margins.';

export const RELEASE_NOTES_HISTORY = [
  {
    version: 'v1.43.278 (Web & PWA)',
    badgeTr: 'Güncel',
    badgeEn: 'Current',
    bulletsTr: [
      'Web Linkten Otomatik Fiyat Çekme Onarıldı: JSON-LD, meta ve gömülü mağaza JSON ayrıştırıcısı Android ile 1:1 eşitlenip güvenilir CORS okuyucuya geçirildi; kargo/taksit satırlarını atlayarak gerçek ürün fiyatını çeker.',
      'İstek Listesi Fiyat Kontrol Butonu: Taşan ikinci buton kaldırılarak Android ile birebir tam genişlikte Fiyat Düşüşlerini Şimdi Kontrol Et butonu eklendi.',
      'Kıyafet Detay Ekranı 1:1 Eşitlemesi: Tek fotoğrafta gereksiz Fotoğrafı Düzenle tuşu ve ok butonları kaldırıldı; çoklu fotoğrafta 2 satırlı düzen (üstte Fotoğrafı Düzenle, altta Kapak Yap + canlı kırmızı Fotoğrafı Sil) eklendi ve üst simgeler büyütüldü.'
    ],
    bulletsEn: [
      'Web Automatic Price Fetching Fixed: Ported Android JSON-LD/meta/embedded-JSON price extractor with reliable CORS fetching that filters out shipping/installment lines.',
      'Full-Width Wishlist Price Check Button: Matched Android with a single full-width Check Price Drops Now button.',
      '1:1 Garment Detail Parity: Removed Edit Photo on single-photo items and overlay arrows, matched 2-row multi-photo actions (Edit Photo / Set Cover + vivid red Delete Photo), and enlarged detail header icons.'
    ]
  },
  {
    version: 'v1.43.277 (Web & PWA)',
    badgeTr: null,
    badgeEn: null,
    bulletsTr: [
      'Web Kıyafet Detayı & Çoklu Fotoğraf Düzeltmesi: Kıyafete tıklandığında fotoğrafın yarısının kesilmesi ve kaydırma sıkışması tamamen giderildi; tam 1:1 kare sahne, ileri/geri okları ve parmakla kaydırarak fotoğraf değiştirme eklendi.',
      'Web Linkten Fiyat Çekme & İndirim Hatırlatıcı: İstek listesine ürün eklerken Linkten Fiyat Çek, detayda Fiyat Kontrol, listede Fiyat Düşüşlerini Şimdi Kontrol Et ve otomatik indirim bildirimi (🔥 İndirim) eklendi.',
      '3D Sanal Kabin Arka Planı Koru Seçeneği: 3D Sanal Kabin üst çubuğuna Arka Planı Kaldır / Arka Planı Koru düğmesi eklendi.',
      'Sohbet & Arayüz 1:1 Eşitlemesi: Web mesaj balonlarında basılı tutunca açılan Mesaj İşlemleri (Düzenle/Sil), sağa hizalı Görüldü/İletildi tikleri, yuvarlatılmış mesaj yazma kutusu ve birebir saç detaylı yüz analizi simgesi güncellendi.'
    ],
    bulletsEn: [
      'Web Garment Detail Layout Fix: Resolved half-clipped item detail view with full 1:1 square photo framing, prev/next arrows, and touch swipe navigation.',
      'Web Wishlist Price Tracker & Discount Alerts: Added Fetch Price from Link, single & bulk Check Price Drops, strikethrough old prices, green 🔥 Price Drop badges, and discount reminders.',
      '3D Virtual Fitting Keep-Background Toggle: Added Remove BG / Keep BG switch to keep original garment backgrounds when desired.',
      '1:1 Chat & Icon Parity: Replaced inline chat edit/delete buttons with long-press Message Actions modal, aligned Seen/Delivered checkmarks, rounded the message composer, and updated FaceRetouchingNatural SVG.'
    ]
  },
  {
    version: 'v1.43.276 (Web & PWA)',
    badgeTr: null,
    badgeEn: null,
    bulletsTr: [
      'Tam Yedekleme & Geri Yükleme (Hesaplı / Hesapsız): Ayarlara tüm kıyafetlerin, fotoğrafların, kombinlerin ve uygulama ayarlarını hesabınla birlikte veya hesapsız olarak tek dosyada yedekleme ve geri yükleme özelliği eklendi.',
      'Hareketler & Bildirim Ayrımı: Gelen mesaj bildirimleri Hareketler & Takip İstekleri listesinden ve kalp sayacından ayrıldı; mesajlar yalnızca Sohbet simgesinde görünür.',
      '1:1 Android ↔ Web Arayüz Eşitlemesi: Profil düzenleme anahtarları, yıldızlı yüz analizi simgesi, sohbet üst başlığında tam kullanıcı adı görünümü, temiz mesaj düzenle/sil butonları, sohbet kamera simgesi ve Dolap Özeti önizleme yazı tipleri Android APK ile 1:1 eşitlendi.'
    ],
    bulletsEn: [
      'Full Backup & Restore (With / Without Account): Added Settings export and restore for all garments, photos, outfits, and preferences — optionally with or without account credentials.',
      'Clean Activity Feed: Incoming direct message alerts no longer clutter Activity & Follow Requests or its badge.',
      '1:1 Android ↔ Web Visual Parity: Aligned profile toggle switches, FaceRetouchingNatural icon, full chat header display name width, clean edit/delete bubble buttons, chat camera icon, and Wardrobe Summary stat typography with Android.'
    ]
  },
  {
    version: 'v1.43.275 (Web & PWA)',
    badgeTr: null,
    badgeEn: null,
    bulletsTr: [
      'Instagram Tarzı Direkt Mesajlaşma (Sohbet): Sosyal başlıkta Ayarlar kaldırılıp Profil sağa kaydırıldı, eski profil yerine okunmamış sayaçlı Sohbet getirildi; takip ettiklerinle Mesajlar ve takip etmediklerin için tek mesajlık Mesaj İstekleri ayrıldı.',
      'Zengin Medya & Canlı Durumlar: 1x/1.5x/2x hızda sesli mesaj, fotoğraf/video çekme ve yükleme, GIF gönderme, balona dokununca HH:mm saati, 15 dk düzenleme (değiştirildi), yumuşak silme (Bu mesaj silindi), sohbeti sessize alma ve canlı çevrimiçi/yazıyor/Görüldü göstergeleri eklendi.',
      'Her Gönderiden Özel Yanıt & 1:1 Profil Eşitliği: Gönderi ⋮ menüsünden Özel Sohbetten Yanıtla ve Sohbette Gönder çalışır; akışta basılı tutarak ve profilde tek tıkla avatar büyür; Web profil arayüzü başlık çizgisiz ve 42px butonlarla Android ile 1:1 eşlendi.',
      '12 Sezon CIE L*a*b* Ten & Kontrast Analizi: Alın/yanak örnekleriyle 12 mevsim analiz, Kaçınılacak Tonlar ve Tüm 12 Sezon manuel seçicisi eklendi.'
    ],
    bulletsEn: [
      'Instagram-Style Direct Messaging (Chat): Moved Profile right, placed Chat with unread badge in its slot, separated Messages and 1-message Message Requests, and added 1x/1.5x/2x voice notes, camera/gallery media, GIFs, HH:mm timestamps, 15m edits, soft deletes, mute, and live indicators.',
      'Universal Post Actions & Username Gate: Added Reply in Private Chat and Send in Chat to every post ⋮ menu, long-press/tap avatar zoom, and @username requirement prompts.',
      '1:1 Profile Modal Pixel-Match: Removed header divider border and aligned 42px button heights, spacing, and typography identically to Android.',
      '12-Season CIE L*a*b* Skin & Contrast Analysis: Separates Cool, Neutral, Olive, and Warm undertones across all 12 seasons with Shades to Avoid and manual season explorer.'
    ]
  },
  {
    version: 'v1.43.274 (Web & PWA)',
    badgeTr: null,
    badgeEn: null,
    bulletsTr: [
      'Dolap Özeti 1:1 Birebir Görünüm: Paylaşılan profillerdeki Dolap Özeti kartı, istatistik kutucukları, Gardırop Renk Paleti çubuğu kenarlığı ve alt kategori etiketleri Android ile Web üzerinde birebir eşlendi.',
      'Profil Başlıkları & Navigasyon Eşitliği: Kullanıcı profillerinde üst başlık Kullanıcı Profili olarak sabitlendi; Web profil ve keşfet pencerelerine sol üst geri ve sağ üst paylaş tuşları eklenip alttaki kapat çubukları kaldırıldı.',
      'Canlı Kırmızı Engelle & Net Biyografi: Takip/Paylaş/Engelle buton yükseklikleri ve köşe yarıçapları Android ve Web üzerinde hizalandı; Engelle tuşu canlı kırmızıya güncellendi, biyografi metinleri keskinleştirildi.',
      'Eşit Tarz Etiketleri & Renk Noktası Kontrastı: Web profildeki tarz etiketleri Android ile aynı yumuşak vurgu kapsüllerine dönüştürüldü ve açık/koyu temalarda renk noktalarına belirgin halka çerçevesi eklendi.'
    ],
    bulletsEn: [
      '1:1 Wardrobe Summary Parity: Matched stat tiles, Wardrobe Color Palette bar borders, color pills, and subcategory chips identically across Android and Web/PWA shared profiles.',
      'Unified Profile Headers & Navigation: Standardized profile view header to User Profile with top-left back navigation and top-right Share action, removing redundant bottom close bars on Web.',
      'Vivid Red Block Action & Crisp Bios: Aligned Follow, Share, and Block button heights and corners 1:1 across platforms, brightened the Block button in both light and dark modes, and sharpened profile bio typography.',
      'Matching Style Vibe Pills & Swatch Contrast: Styled Web profile vibe tags with the same soft accent capsules as Android and added high-contrast swatch rings for light and dark themes.'
    ]
  },
  {
    version: 'v1.43.273 (Web & PWA)',
    badgeTr: null,
    badgeEn: null,
    bulletsTr: [
      'Mobil & Web Birebir Tasarım Eşitlemesi: Sosyal akıştaki gönderi aksiyonları şeffaf ikonlu temiz görünüme kavuşturuldu; + Paylaş tuşu sağ alt köşeye sabitlenip Keşfet & Ara üst başlığa alındı.',
      '2 Sekmeli Profil & Gelişmiş Kullanıcı Vitrinleri: Profil penceresi sadeleşti (Profilim · Düzenle), takipçi/takip kutucukları, engel/paylaş düğmeleri ve 2 sütunlu vitrin kartları Android ve Web ile birebir hizalandı.',
      'Gardırop Renk Paleti & 20 Tarz Etiketi: Paylaşılan profillere Gardırop Renk Paleti spektrum çubuğu eklendi, tarz etiketi seçenekleri 20ye çıkarıldı ve aç/kapa anahtarları yenilendi.',
      'Akıllı Klavye Odak Düzeni: Metin kutularına yazarken ekranın yukarı taşması hem Android hem Web/PWA içinde giderildi.'
    ],
    bulletsEn: [
      '1:1 Mobile & Web Parity: Clean transparent post action buttons in the Social stream, floating + Share pill FAB at bottom-right, and dedicated Discover button in the top bar.',
      'Streamlined 2-Tab Profile & Unified Showcase: Clean My Profile | Edit tabs, balanced two-row Follow/Share/Block actions, 2-column theme-adaptive showcase grid, and filter pills across Android and Web.',
      'Wardrobe Color Palette & 20 Style Tags: Added toggleable Wardrobe Color Palette spectrum bar to shared profiles, expanded style vibes to 20 presets (max 4), and unified pill switches.',
      'Smarter Keyboard Avoidance: Focused text inputs stay comfortably visible inside sheets and editors across Android and mobile browsers.'
    ]
  },
  {
    version: 'v1.43.272 (Web & PWA)',
    badgeTr: null,
    badgeEn: null,
    bulletsTr: [
      'Kullanıcı Adı & Türkçe Karakter Desteği: @kyhlaer ve Türkçe karakterler (ç, ğ, ı, İ, ö, ş, ü) profil ve paylaşım akışlarında otomatik olarak temizlenip desteklenir.',
      'Kombin & Kıyafet Paylaşım İyileştirmeleri: Kombin önerisi ve kayıtlı kombin paylaşım hatası giderildi; kart ve kombin paylaşım butonları her temaya uyumlu şık daire ikonlarına dönüştürüldü.',
      'Dengeli Sosyal Başlık & Tam Ekran Profil Düzenleme: Sosyal üst aksiyon butonları eşit aralıklarla hizalandı; Profil penceresi tam ekran yapıldı, 3 sekme (Profilim, Düzenle, Keşfet & Ara) tek satıra sığdırıldı ve Kaydet & Yayınla altta sabitlendi.',
      'Kalıcı Kombin İlhamı Cinsiyet Seçimi: Kombin İlhamı ve Stil Keşfi içinde seçtiğin Erkek veya Kadın tercihi artık kaydedilip sayfa yenilendikten sonra da hatırlanır.'
    ],
    bulletsEn: [
      'Username & Turkish Character Normalization: @kyhlaer and Turkish letters normalize seamlessly across profile saves and share links.',
      'Fixed Outfit Sharing & Sleek Share Buttons: Resolved suggested outfit link sharing and styled card/outfit share icons to adapt cleanly in dark & light themes.',
      'Aligned Social Header & Fullscreen Profile Editor: Equal-height 36px Social actions with uniform spacing, true fullscreen Profile Hub with single-row 3 tabs, and sticky footer Save & Publish.',
      'Persistent Inspiration Gender Selection: Your last chosen Men or Women toggle in Inspiration & Style Discovery stays saved across reloads.'
    ]
  },
  {
    version: 'v1.43.271 (Web & PWA)',
    badgeTr: null,
    badgeEn: null,
    bulletsTr: [
      'Ortadaki Sosyal Akış Sekmesi & Birleşik Simge: Alt menünün ortasındaki yeni Sosyal sekmesinden kombin, look, çoklu fotoğraf ve kısa video paylaşabilir; fotoğraf/GIF yorumları ve 15 dk açıklama düzenleme kullanabilirsin.',
      'Kombin Linki Paylaşımı & Tek Tıkla Dolaba Ekleme: Önerilen, kayıtlı ve düzenlenen tüm kombinleri @kullanıcıadınla link olarak paylaşabilir, hem Web hem Android üzerinden açıp tek tıkla kaydedebilirsin.',
      'Kullanıcı Engelleme, Sade Üst Bar & Ayarlar: Profillerden kullanıcı engelleyip Sosyal > Profil > Düzenle kısmından engelleri kaldırabilirsin. Profil ve Kalp butonları artık yalnızca Sosyal sekmesinde yer alır; Ayarlar altına GitHub kısayolu eklendi.',
      'Şu Saatten İtibaren 24 Saatlik Hava Tahmini: Bugünkü saatlik hava durumu artık tam bulunduğun saatten başlayarak gece yarısı üzerinden yarına uzanan 24 saati listeler.'
    ],
    bulletsEn: [
      'Center Social Feed Tab & Unified Icon: Share outfits, looks, multi-photos, and short videos with photo/GIF comments, 15-min caption edits, and deletions from the centered Social tab.',
      'Outfit Link Sharing & One-Click Closet Import: Share recommended, saved, and custom outfits via cross-platform Web & Android deep-links with full wardrobe import.',
      'User Blocking, Streamlined Headers & Settings GitHub Link: Block/unblock accounts inside Social Profile, keep Profile/Heart controls scoped cleanly to Social, and jump to Kyhlaer on GitHub from Settings.',
      'Rolling 24-Hour Weather Forecast: Hourly weather on Today starts right from the current hour and continues seamlessly into tomorrow.'
    ]
  },
  {
    version: 'v1.43.270 (Web & PWA)',
    badgeTr: null,
    badgeEn: null,
    bulletsTr: [
      'Saatlik 24 Saat Hava Durumu: Kombinler sekmesindeki hava durumu kartına dokunarak günün 24 saatini sıcaklık, hissedilen, yağış ihtimali ve rüzgarıyla saat saat inceleyebilirsin.',
      'Kıyafet Linki Paylaşımı & Tek Tıkla Kaydetme: Dolabım kartlarından ve detay ekranından kıyafetini fotoğrafı ve @kullanıcıadınla link olarak paylaşabilir, gelen linki hem Web hem mobilden açıp beğenebilir veya dolabına/istek listene ekleyebilirsin.',
      'Herkese Açık / Gizli Profil & Vitrin Paylaşımı: Kombinler, İlham ve Özet sekmelerinde Ayarlar ikonunun solundaki Profil tuşundan profil fotoğrafı, kullanıcı adı, biyografi, takip/takipçi ve özel dolap özeti istatistik filtreleriyle profilini düzenleyip sergileyebilir, arkadaşlarını arayıp takip edebilirsin.'
    ],
    bulletsEn: [
      '24-Hour Forecast View: Tap the weather summary card in Outfits to inspect hourly temperature, feels-like, rain probability, and wind speed.',
      'Garment Link Sharing & One-Tap Save: Share any garment card or detail as a deep-link with your @username & avatar; recipients can view, like, and import it into Wishlist or Closet.',
      'Public / Private Profiles, Stats Privacy & Follows: Manage your avatar, @username, bio, curated Showcase sections, filtered Wardrobe Summary metrics, search, and follower requests from the top-bar Profile button.'
    ]
  },
  {
    version: 'v1.43.263 (Web & PWA)',
    badgeTr: null,
    badgeEn: null,
    bulletsTr: [
      'Dolabım Sürükle-Bırak Ekran Kaydırma: Kıyafet kartını basılı tutup ekranın üst veya alt kenarına yaklaştırdığında sayfa artık akıcı şekilde yukarı/aşağı kayar; ekran dışındaki sıralara da kesintisiz taşıma yapılır.'
    ],
    bulletsEn: [
      'Wardrobe Drag Edge Auto-Scroll: Long-pressing and dragging a garment near the top or bottom edge smoothly auto-scrolls the page so items can be reordered across the entire wardrobe.'
    ]
  },
  {
    version: 'v1.43.262 (Web & PWA)',
    badgeTr: null,
    badgeEn: null,
    bulletsTr: [
      'Katalog Fotoğrafı Hata Ayrımı: Hugging Face sunucu yoğunluğu veya ZeroGPU limitlerinde yanlış "İnternet bağlantını kontrol et" uyarısı düzeltildi; sunucu yoğunluğu ve kota mesajları doğru ayrıldı.',
      'Kıyafet Düzenleme & Tek Fotoğraf Kırpma: "Kıyafeti Düzenle -> Açı Ekle" akışındaki yeni ürün açma hatası giderildi; ekleme ve detay ekranından tekil fotoğrafları doğrudan kırpma ("Fotoğrafı Düzenle") eklendi.',
      'Dolabım Sürükle-Bırak Sıralama & Rastgele İlham: Dolabım ekranında kıyafetleri basılı tutarak sıralama ve her açılışta yenilenen Kombin İlhamı sırası eklendi.',
      'Yumuşak Sekme Geçişi, Kalıcı Konum & Klavye Uyumu: Kayar alt menü hapı ve yumuşak yatay geçişler, seçilen hava durumu konumunun hatırlanması ve Not alanında klavye açılınca formun yukarı kayması sağlandı.'
    ],
    bulletsEn: [
      'Catalog AI Error Clarity: Fixed false "Check internet connection" toasts when Hugging Face ZeroGPU spaces report busy/quota errors.',
      'Angle Append & Per-Photo Crop: Fixed "Edit Item -> Add Angle" creating a duplicate item and added single-photo re-cropping from both Edit and Detail views.',
      'Drag-and-Drop Wardrobe Ordering & Fresh Lookbook: Added long-press drag reordering in Wardrobe and per-visit randomized Inspiration ordering.',
      'Smooth Dock Transitions, Persistent Location & Keyboard Avoidance: Added sliding dock pill & transitions, persistent city/GPS weather selection, and automatic scroll above mobile keyboards.'
    ]
  },
  {
    version: 'v1.43.261 (Web & PWA)',
    badgeTr: null,
    badgeEn: null,
    bulletsTr: [
      'Arka Planı Kaldır & Katalog Ayrımı: Daha önce katalog fotoğrafına dönüştürülmüş parçalarda dahi "Arka Planı Kaldır" tıklandığında saf arka planı kaldırılmış (bgrm_) kesime geçilir/yeniden üretilir; katalog görseli ayrı saklanır.',
      'Katalog Fotoğrafı Kullan Geçişi: Katalog fotoğrafı oluşturulduktan sonra buton "Katalog Fotoğrafı Kullan" haline dönüşür ve üç mod arasında AI isteği atmadan anında geçiş sağlar.',
      'Fotoğraf Ekle & Açı Ekle Eşit Boyut: Ekleme/düzenleme ve detay pencerelerindeki Fotoğraf Ekle / Açı Ekle (Kamera / Galeri) butonları 48px eşit yükseklik ve eşit genişliğe getirildi.'
    ],
    bulletsEn: [
      'Separate Remove BG & Catalog Photos: Clicking "Remove BG" on previously catalog-converted pieces regenerates/switches to a pure offline cutout (bgrm_) while storing catalog photos separately (catalog_).',
      '"Use Catalog Photo" Toggle: Once generated, "Create Catalog Photo" turns into "Use Catalog Photo" for instant mode switching without spending extra AI calls.',
      'Equalized Photo Action Buttons: Matched 48px height, equal flex width, and single-line label alignment on Add Photo / Add Angle and Camera / Gallery buttons.'
    ]
  },
  {
    version: 'v1.43.26 (Web & PWA)',
    badgeTr: null,
    badgeEn: null,
    bulletsTr: [
      'Evrensel Görsel Format & Sınırsız Boyut Desteği: HEIC, HEIF, AVIF, WebP, GIF, BMP, TIFF, SVG dahil tüm fotoğraf formatları ve yüksek çözünürlüklü büyük dosyalar boyut/piksel hatası vermeden çok katmanlı tarayıcı motoruyla açılarak temizlenir.',
      'Akıllı Alt Menü Kaydırma Geçişi: Web sürümünde aktif alt sekme ikonuna dokununca sayfa yumuşak şekilde en üste çıkar; en üstteyken tekrar basınca az önceki konumuna geri döner.',
      '4 Yönlü Mobil Esneme Efekti (Bouncy Overscroll): Yukarı, aşağı, sağa ve sola kaydırmada sayfa/satır/modal sonuna gelindiğinde mobildeki gibi yaylı geri sekme efekti eklendi.',
      '1:1 Mobil Ana Ekran Logosu: Web uygulamasını telefona yüklerken oluşan ince askı simgesi düzeltildi; Android adaptif maskable + standart ikonlar mobil APK simgesiyle birebir aynı kalınlık ve orana getirildi.'
    ],
    bulletsEn: [
      'Universal Image Format & File Size Support: Removed restrictive MIME, magic-byte, byte-size, and resolution ceilings; added a 4-stage canvas/ImageBitmap decoder supporting HEIC, HEIF, AVIF, WebP, GIF, BMP, TIFF, and SVG at any resolution.',
      'Smart Bottom-Dock Scroll Toggle: Tapping the active bottom navigation tab smoothly scrolls to the top and remembers the previous offset so a second tap restores your exact spot.',
      '4-Direction Bouncy Overscroll: Added spring rubber-band overscroll feedback on vertical top/bottom boundaries, modals, horizontal chip strips, and outer tab edges.',
      '1:1 Mobile Launcher Icon Parity: Rebuilt Web & PWA icons (`any` + `maskable`) directly from Android ic_launcher vector paths so installed web apps match mobile icon stroke thickness identically.'
    ]
  },
  {
    version: 'v1.43.25 (Web & PWA)',
    badgeTr: null,
    badgeEn: null,
    bulletsTr: [
      'Android Web Dikey Kaydırma Düzeltmesi: Kök kaydırma kilidi ve body overscroll engeli kaldırıldı; Android telefonlarda tüm sekmelerde tek parmak dikey kaydırma ve sağa/sola sekme geçişi pürüzsüz hale getirildi.',
      'Detaylı Filtrele Renk & Buton Eşitlemesi: Boş dolapta da 6 temel renk + "Diğer Renkler (+N)" açılır görünümü getirildi ve "Filtreleri Temizle" butonu dar ekranlarda kesilmeyecek şekilde hizalandı.',
      'Mağaza Arama Linkleri Düzeltildi: Bershka (?gender=man/woman) ve Zara (&section=MAN/WOMAN) cinsiyet parametreleri düzeltildi; Pull&Bear yerine doğrudan arama destekleyen Hepsiburada ve Boyner eklendi.',
      'Tam Güvenlik Sertleştirmesi: Web SSRF IPv6/IPv4 atlatma kontrolleri genişletildi; Android anahtar şifreleme (AndroidKeyStore AES-256-GCM), depo/GitHub CI ve güvenlik politikaları sıkılaştırıldı.'
    ],
    bulletsEn: [
      'Android Web Vertical Scroll Fix: Removed root viewport height/overflow scroll traps and body overscroll containment so single-finger vertical scrolling and horizontal tab swipes work cleanly on Android phones.',
      'Filter Modal Color Parity & Button Fix: Matched 6 core colors + expandable "Other Colors (+N)" toggle even on empty wardrobes and fixed button widths so Clear Filters never truncates.',
      'Fixed Store Search Links: Updated Bershka (?gender=man/woman) and Zara (&section=MAN/WOMAN) with compound-query cleanup and added direct Hepsiburada & Boyner search instead of broken Pull&Bear redirects.',
      'Full Security Hardening: Strengthened IPv6 NAT64/Teredo/6to4/IPv4-mapped & obfuscated IPv4 SSRF guards across Web & Android alongside encrypted keystore secrets and GitHub security controls.'
    ]
  },
  {
    version: 'v1.43.24 (Web & PWA)',
    badgeTr: null,
    badgeEn: null,
    bulletsTr: [
      'Resmi Katalog AI Model Zinciri (FLUX.2 / Qwen-2511 / FireRed-1.1): 4 adımlı bozulmaya yol açan eski modeller kaldırıldı; FLUX.2-dev, FLUX.2-klein-9B (Base), Qwen-Image-Edit-2511, FireRed-Image-Edit-1.1 ve FLUX.1-Kontext-Dev resmi modelleriyle ürünün baskı, renk ve açısını birebir koruyan stüdyo motoruna geçildi.',
      'Sıkı Ürün Kimliği & Açı Kilidi + Yüksek Çözünürlüklü Girdi: Arka/ön açı kilidi, tam baskı koruma promptu, doğal oranda 1024px yükleme ve akıllı çerçeve/kenar temizliği eklendi.'
    ],
    bulletsEn: [
      'Official Catalog AI Model Chain (FLUX.2 / Qwen-2511 / FireRed-1.1): Replaced 4-step distilled Spaces with official full-fidelity FLUX.2-dev, FLUX.2-klein-9B Base, Qwen-Image-Edit-2511, FireRed-Image-Edit-1.1, and FLUX.1-Kontext-Dev models.',
      'Strict Garment Identity & Viewing-Side Lock: Enforced same-side camera preservation, full-size print fidelity prompt, natural aspect-ratio 1024px uploads, and speckle-resistant 87% studio framing.'
    ]
  },
  {
    version: 'v1.43.23 (Web & PWA)',
    badgeTr: null,
    badgeEn: null,
    bulletsTr: [
      'API Gerektirmeyen Arka Plan Kaldırma & Yeni Düğme: Kıyafet ekleme ve detay ekranındaki geçiş düğmesi "Arka Planı Kaldır" olarak yenilendi; Web sürümü de internetsiz çoklu-küme CIE Lab segmentasyonuyla masa/zemin karmaşasını temizler.',
      'Web Tek Parmakla Kaydırma, Sekme Kaydırma & Akıllı Geri Tuşu: Sayfa ve modal kaydırma pürüzsüzleştirildi, sağa/sola kaydırarak sekmeler arası geçiş eklendi ve geri tuşu artık uygulamayı kapatmadan önce açık modalı, aramayı ve önceki sekmeyi kapatır.',
      'Stil Keşfi 1:1 Tasarım: Erkek/Kadın geçişi, kaydırılabilir Keşfet/Beğendiklerim/Geçtiklerim sekmeleri, altta yüzen bilgi kartı ve Geri Al düğmeli tam ekran kart düzeni getirildi.',
      'Play Store Tarzı Küçük Güncellemeler (Android): Sıkıştırılmış (~3x küçük) kurulum paketi ve uygulama içi ikili delta yama (DPATCH1) desteği eklendi.'
    ],
    bulletsEn: [
      'Zero-API Offline Background Remover: Replaced "Studio" toggle with "Remove BG", adding multi-cluster CIE Lab segmentation on Web for clean clutter-free garment cutouts.',
      'Smooth Web Gestures & Back Navigation: Fixed single-finger scrolling, added horizontal swipe tab switching, and added full browser back-button modal/tab history handling.',
      '1:1 Style Discovery Experience: Redesigned fullscreen swipe cards with Men/Women toggle, floating bottom info card, and Undo support.',
      'Smaller APK & Play Store-Style Delta Updates (Android): Compressed native libraries and binary DPATCH1 delta update engine for lightweight future app updates.'
    ]
  },
  {
    version: 'v1.43.22 (Web & PWA)',
    badgeTr: null,
    badgeEn: null,
    bulletsTr: [
      'AI Katalog & Vertex Express Desteği: Ayarlar’da hem AIza... hem AQ.... (Google Cloud / Vertex AI Express) Gemini API anahtarlarıyla uyumlu katalog fotoğrafı ve 3D Kabin üretimi; anahtarsız ücretsiz motor ve opsiyonel HF tokenı korunur.',
      'Mobil & Web 1:1 Detaylı Filtrele Uyumu: Mobil "Detaylı Filtrele" penceresi Web sürümüyle birebir aynı modal düzenine getirildi (sahip olunan renkler öncelikli, genişletilebilir tüm renkler ve Stil filtresi).',
      'Tam Siber Güvenlik Altyapısı: Katı CSP, clickjacking engelleme, AES-256-GCM şifreli anahtar deposu, SSRF/redirect korumalı ağ katmanı, sihirli bayt dosya doğrulama ve EXIF temizliği uygulandı.'
    ],
    bulletsEn: [
      'AI Studio & Vertex Express Support: Supports both AIza... and AQ.... Gemini/Vertex AI Express API keys for catalog flat-lay generation and 3D Try-On alongside the zero-key free engine.',
      '1:1 Mobile & Web Filter Parity: Aligned mobile "Filter Wardrobe" dialog with Web modal layout, owned-first color sorting, expandable palette toggle, and Style chips.',
      'Comprehensive Web Security Hardening: Strict CSP, anti-framing guard, AES-256-GCM non-extractable keystore, SSRF fetch guards, upload magic-byte checks, and automatic EXIF stripping.'
    ]
  },
  {
    version: 'v1.43.21 (Web & PWA)',
    badgeTr: null,
    badgeEn: null,
    bulletsTr: [
      'Gerçek AI Katalog Fotoğrafı Motoru: Kıyafet fotoğraflarını sadece arka plan kesmek yerine yapay zeka ile kırışıklıkları düzeltilmiş, kolları ve formu simetrik hizalanmış, #FFFFFF stüdyo katalog fotoğrafına dönüştürür (anahtarsız ücretsiz çalışır).',
      'Sadeleştirilmiş Arayüz & Ayarlar: "Stüdyo / Orijinal" geçişi ve "Katalog Fotoğrafı Oluştur" düğmesi yenilendi; Ayarlar’a daha yüksek günlük limit için opsiyonel ücretsiz Hugging Face token desteği eklendi.'
    ],
    bulletsEn: [
      'Generative AI Catalog Photo Engine: Reconstructs real garment photos into steamed, symmetrically arranged flat-lay catalog shots on pure #FFFFFF studio backgrounds with zero API key required.',
      'Clean Studio UI & Settings: Simplified "Studio / Original" switcher and "Create Catalog Photo" action, plus optional free Hugging Face token in Settings for extended daily quota.'
    ]
  },
  {
    version: 'v1.43.2 (Web & PWA)',
    badgeTr: null,
    badgeEn: null,
    bulletsTr: [
      'Tarayıcı & iPhone PWA Sürümü: Android uygulamasının tüm özellikleri birebir tarayıcıya taşındı; App Store ücreti olmadan Safari/Chrome üzerinden kullanılabilir ve tek dokunuşla "Ana Ekrana Ekle" ile tam ekran uygulama gibi açılır.',
      'E-Ticaret Stüdyo Flat-Lay Dönüştürücü: Arka plan/askı temizleme, kırışıklık gölgesi yumuşatma, #FFFFFF 1:1 stüdyo hizalama ve Ayarlar’dan opsiyonel Gemini Nano Banana 2.1 AI desteği.',
      '3D Sanal Kabin: Kendi boy fotoğrafının silüet & yüz/omuz noktalarını algılayıp kıyafetleri 3D silindirik kıvrım + ışık sıcaklığı uyumu + AI Gerçekçi Giydirme ile dener.',
      'Tam Gizlilik: Fotoğraflar ve dolap verileri cihazındaki tarayıcı IndexedDB hafızasında yerel olarak saklanır.'
    ],
    bulletsEn: [
      'Browser & iPhone PWA Edition: 1:1 port of the Android app with Service Worker offline cache and one-tap "Add to Home Screen" prompt for iPhone Safari & Chrome.',
      'E-Commerce Studio Flat-Lay Transformer: Strips hangers & backgrounds, softens crease shadows on pure #FFFFFF 1:1 canvas, plus optional Gemini Nano Banana 2.1 synthesis.',
      '3D Virtual Fitting Room: Segments body silhouette & anchors to fit and curve garments in 3D with ambient lighting harmony plus optional AI photorealistic try-on.',
      'Private On-Device Storage: Photos and wardrobe data stay locally in your browser IndexedDB.'
    ]
  },
  {
    version: 'v1.43.1',
    badgeTr: null,
    badgeEn: null,
    bulletsTr: [
      'Yeni minimalist askı logosu, istek listesi link/fiyat iyileştirmeleri ve 9 doğrudan Türkçe mağaza arama bağlantısı.',
      'Çince (中文) dil desteği, bağımsız yüz & kabin fotoğrafları, 82 kombin ilhamı + Stil Keşfi kaydırma ve Akıllı Bavul Hazırlayıcı.'
    ],
    bulletsEn: [
      'Minimalist hanger icon, improved store search queries across 9 retailers, and 6 languages (TR, EN, DE, FR, ES, ZH).',
      '82 Lookbook outfits + Tinder-style Swipe Discovery and destination weather Smart Suitcase Planner.'
    ]
  }
];
