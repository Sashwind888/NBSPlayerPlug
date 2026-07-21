// nbs-decoder.js
const http = require('http');
const https = require('https');
const { URL } = require('url');

// -------------------- BinaryReader (小端序) --------------------
class BinaryReader {
    constructor(buffer) {
        this.buffer = buffer;
        this.offset = 0;
    }

    readByte() {
        return this.buffer.readUInt8(this.offset++);
    }

    readShort() {
        const val = this.buffer.readUInt16LE(this.offset);
        this.offset += 2;
        return val;
    }

    readInt() {
        const val = this.buffer.readUInt32LE(this.offset);
        this.offset += 4;
        return val;
    }

    // 字符串按原始字节（latin1）读取，与官方一致
    readString(length) {
        if (length === 0) return '';
        const str = this.buffer.toString('latin1', this.offset, this.offset + length);
        this.offset += length;
        return str;
    }
}

// -------------------- 数据模型 --------------------
class Line {
    constructor() {
        /** @type {{ instrument: number, key: number }[]} */
        this.blocks = [];
    }
}

// -------------------- 解码器 (严格参照官方 NBSDecoder) --------------------
class Decoder {
    constructor(buffer) {
        this.reader = new BinaryReader(buffer);

        this.lines = [];
        this.version = 0;
        this.name = '';
        this.author = '';
        this.originAuthor = '';
        this.description = '';
        this.length = 0;
        this.height = 0;
        this.bpm = 0;
        this.firstCustomInstrument = 0;   // 第一个自定义乐器索引
        this.beat = 0;
        this.loop = 0;
        this.loopCount = 0;
        this.loopStart = 0;

        try {
            // 1. 读取长度/版本标志
            let length0 = this.reader.readShort();
            if (length0 === 0) {
                // 新格式（版本 ≥ 0）
                this.version = this.reader.readByte();
                this.firstCustomInstrument = this.reader.readByte();
                if (this.version >= 3) {
                    this.length = this.reader.readShort();
                } else {
                    // 版本 < 3 时 length 会在后面通过 tick 计算，暂设为 0
                    this.length = 0;
                }
            } else {
                // 旧格式（版本 0）
                this.version = 0;
                this.length = length0;
                this.firstCustomInstrument = 10;   // 默认值（向后兼容）
            }

            // 2. 歌曲高度（图层数）
            this.height = this.reader.readShort();

            // 3. 字符串信息
            this.name = this.reader.readString(this.reader.readInt());
            this.author = this.reader.readString(this.reader.readInt());
            this.originAuthor = this.reader.readString(this.reader.readInt());
            this.description = this.reader.readString(this.reader.readInt());

            // 4. 速度
            const tempo = this.reader.readShort();
            this.bpm = tempo / 100;

            // 5. 其他元数据（按官方顺序）
            this.reader.readByte();                 // autoSave (boolean)
            this.reader.readByte();                 // autoSaveDuration
            this.beat = this.reader.readByte();     // timeSignature
            this.reader.readInt();                  // minutesSpent
            this.reader.readInt();                  // leftClicks
            this.reader.readInt();                  // rightClicks
            this.reader.readInt();                  // blocksAdded
            this.reader.readInt();                  // blocksRemoved
            // 导入文件名（总是读取，无论版本）
            this.reader.readString(this.reader.readInt());

            // 6. 循环信息（版本 ≥ 4）
            if (this.version >= 4) {
                this.loop = this.reader.readByte();
                this.loopCount = this.reader.readByte();
                this.loopStart = this.reader.readShort();
            }

            // 7. 读取音符数据
            let tick = -1;
            while (true) {
                const jumpTicks = this.reader.readShort();
                if (jumpTicks === 0) break;
                tick += jumpTicks;

                let layer = -1;
                const line = new Line();

                while (true) {
                    const jumpLayers = this.reader.readShort();
                    if (jumpLayers === 0) break;
                    layer += jumpLayers;

                    const instrument = this.reader.readByte();
                    const key = this.reader.readByte();

                    if (this.version >= 4) {
                        // velocity, panning, pitch (忽略)
                        this.reader.readByte();       // velocity
                        this.reader.readByte();       // panning (0~200, 实际需转换)
                        this.reader.readShort();      // pitch
                    }

                    line.blocks.push({ instrument, key });
                }

                this.lines.push(line);
            }

            // 注意：官方还会读取图层名称、音量、pan 以及自定义乐器，
            // 但我们只关心音符数据，故在此停止，不会影响已解析的内容。
            // 如果需要完整信息，可以继续补充，但非必需。

        } catch (err) {
            console.error('数据解析失败:', err);
        }
    }

    // ---------- 访问器 ----------
    getLines() {
        return this.lines;
    }

    getBPM() {
        return this.bpm;
    }

    getLength() {
        return this.length;
    }
}

// -------------------- 网络下载 --------------------
function fetchUrl(urlStr) {
    return new Promise((resolve, reject) => {
        const parsed = new URL(urlStr);
        const protocol = parsed.protocol === 'https:' ? https : http;
        const req = protocol.get(urlStr, (res) => {
            if (res.statusCode !== 200) {
                reject(new Error(`HTTP ${res.statusCode} ${res.statusMessage}`));
                return;
            }
            const chunks = [];
            res.on('data', (chunk) => chunks.push(chunk));
            res.on('end', () => resolve(Buffer.concat(chunks)));
        });
        req.on('error', reject);
        req.end();
    });
}

// -------------------- 主函数 --------------------
async function main() {
    const args = process.argv.slice(2);
    if (args.length === 0) {
        console.error('用法: node nbs-decoder.js <NBS文件URL>');
        process.exit(1);
    }

    const url = args[0];
    try {
        console.log(`正在下载: ${url}`);
        const buffer = await fetchUrl(url);
        console.log(`下载完成，共 ${buffer.length} 字节`);

        const decoder = new Decoder(buffer);

        // 输出解析结果
        console.log('\n========== 歌曲信息 ==========');
        console.log(`  名称          : ${decoder.name || '(无)'}`);
        console.log(`  作者          : ${decoder.author || '(无)'}`);
        console.log(`  原作者        : ${decoder.originAuthor || '(无)'}`);
        console.log(`  描述          : ${decoder.description || '(无)'}`);
        console.log(`  文件版本      : ${decoder.version}`);
        console.log(`  图层数        : ${decoder.height}`);
        console.log(`  总 Tick 数    : ${decoder.length}`);
        console.log(`  BPM           : ${decoder.bpm.toFixed(2)}`);
        console.log(`  拍号          : ${decoder.beat}`);
        console.log(`  循环          : ${decoder.loop ? '是' : '否'}`);
        if (decoder.loop) {
            console.log(`  循环次数      : ${decoder.loopCount}`);
            console.log(`  循环起始 Tick : ${decoder.loopStart}`);
        }

        // 统计音符
        let totalNotes = 0;
        let maxNotesInTick = 0;
        let tickWithMaxNotes = -1;
        decoder.lines.forEach((line, idx) => {
            const count = line.blocks.length;
            totalNotes += count;
            if (count > maxNotesInTick) {
                maxNotesInTick = count;
                tickWithMaxNotes = idx;
            }
        });

        console.log(`  有效 Tick 数  : ${decoder.lines.length}`);
        console.log(`  总音符数      : ${totalNotes}`);
        console.log(`  单 Tick 最多  : ${maxNotesInTick} 个音符 (Tick ${tickWithMaxNotes})`);
        console.log('================================\n');
    } catch (err) {
        console.error('发生错误:', err.message);
        process.exit(1);
    }
}

// 执行
if (require.main === module) {
    main();
}