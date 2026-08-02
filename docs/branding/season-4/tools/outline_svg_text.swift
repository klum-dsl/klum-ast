/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 Stephan Pauxberger
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import Foundation
import CoreText
import CoreGraphics

struct Options {
    let font: String
    let size: CGFloat
    let tracking: CGFloat
    let text: String
    let baseline: CGFloat
}

func glyphPath(for options: Options) -> CGPath {
    let font = CTFontCreateWithName(options.font as CFString, options.size, nil)
    let attributes = [NSAttributedString.Key(kCTFontAttributeName as String): font]
    let line = CTLineCreateWithAttributedString(NSAttributedString(string: options.text, attributes: attributes))
    let path = CGMutablePath()
    let runs = CTLineGetGlyphRuns(line) as NSArray

    for runValue in runs {
        let run = runValue as! CTRun
        let count = CTRunGetGlyphCount(run)
        var glyphs = Array(repeating: CGGlyph(), count: count)
        var positions = Array(repeating: CGPoint.zero, count: count)
        CTRunGetGlyphs(run, CFRange(location: 0, length: 0), &glyphs)
        CTRunGetPositions(run, CFRange(location: 0, length: 0), &positions)

        for index in 0..<count {
            guard let glyph = CTFontCreatePathForGlyph(font, glyphs[index], nil) else { continue }
            let transform = CGAffineTransform(a: 1, b: 0, c: 0, d: -1,
                                              tx: positions[index].x + CGFloat(index) * options.tracking,
                                              ty: options.baseline)
            path.addPath(glyph, transform: transform)
        }
    }
    return path
}

func advance(for options: Options) -> CGFloat {
    let font = CTFontCreateWithName(options.font as CFString, options.size, nil)
    let attributes = [NSAttributedString.Key(kCTFontAttributeName as String): font]
    let line = CTLineCreateWithAttributedString(NSAttributedString(string: options.text, attributes: attributes))
    let glyphCount = (CTLineGetGlyphRuns(line) as NSArray).reduce(0) { total, runValue in
        total + CTRunGetGlyphCount(runValue as! CTRun)
    }
    return CGFloat(CTLineGetTypographicBounds(line, nil, nil, nil)) + CGFloat(max(glyphCount - 1, 0)) * options.tracking
}

func pathData(_ path: CGPath) -> String {
    var commands: [String] = []
    path.applyWithBlock { pointer in
        let element = pointer.pointee
        let points = element.points
        switch element.type {
        case .moveToPoint:
            commands.append("M \(points[0].x) \(points[0].y)")
        case .addLineToPoint:
            commands.append("L \(points[0].x) \(points[0].y)")
        case .addQuadCurveToPoint:
            commands.append("Q \(points[0].x) \(points[0].y) \(points[1].x) \(points[1].y)")
        case .addCurveToPoint:
            commands.append("C \(points[0].x) \(points[0].y) \(points[1].x) \(points[1].y) \(points[2].x) \(points[2].y)")
        case .closeSubpath:
            commands.append("Z")
        @unknown default:
            fatalError("Unsupported path element")
        }
    }
    return commands.joined(separator: " ")
}

let arguments = CommandLine.arguments
guard arguments.count == 7,
      let size = Double(arguments[2]),
      let tracking = Double(arguments[3]),
      let baseline = Double(arguments[5]) else {
    fputs("Usage: outline_svg_text <font> <size> <tracking> <text> <baseline> <id>\\n", stderr)
    exit(2)
}
let options = Options(font: arguments[1], size: CGFloat(size), tracking: CGFloat(tracking), text: arguments[4], baseline: CGFloat(baseline))
let path = glyphPath(for: options)
let bounds = path.boundingBoxOfPath
print("<path id=\"\(arguments[6])\" d=\"\(pathData(path))\" data-bounds=\"\(bounds.minX),\(bounds.minY),\(bounds.maxX),\(bounds.maxY)\" data-advance=\"\(advance(for: options))\"/>")
