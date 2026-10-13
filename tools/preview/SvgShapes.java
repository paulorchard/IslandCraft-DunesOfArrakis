import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import javax.imageio.ImageIO;

/**
 * Reads the hand-drawn continent shapes (an SVG of closed bezier paths in 5000 x 5000 user units, 10 m per unit, origin at the centre, north up)
 * and reports, per fill colour (one colour = one continent): how many shapes, area, bounding box; then overlaps and gaps between colours.
 *   java SvgShapes.java <file.svg> <out.png> [reportOnly]
 * Paths are read from the "Elipses" group only (the guide lines are ignored).
 */
public class SvgShapes {
    record Shape2(String cls, String fill, Path2D.Double path, Area area) {}

    public static void main(String[] a) throws Exception {
        String svg = new String(Files.readAllBytes(Paths.get(a[0])));
        svg = svg.replaceAll("base64,[A-Za-z0-9+/=]+", "base64,");
        Map<String, String> fills = new LinkedHashMap<>();
        Matcher sm = Pattern.compile("\\.(cls-\\d+)\\{fill:(#[0-9a-fA-F]{6})").matcher(svg);
        while (sm.find()) fills.put(sm.group(1), sm.group(2));
        int g0 = svg.indexOf("<g id=\"Elipses\""), g1 = svg.indexOf("</g>", g0);
        String grp = svg.substring(g0, g1);
        Matcher pm = Pattern.compile("<path d=\"([^\"]+)\" class=\"(cls-\\d+)\"").matcher(grp);
        List<Shape2> shapes = new ArrayList<>();
        while (pm.find()) {
            Path2D.Double p = parse(pm.group(1));
            shapes.add(new Shape2(pm.group(2), fills.get(pm.group(2)), p, new Area(p)));
        }
        Map<String, List<Shape2>> by = new LinkedHashMap<>();
        for (Shape2 s : shapes) by.computeIfAbsent(s.fill, k -> new ArrayList<>()).add(s);
        System.out.println("shapes: " + shapes.size() + ", colours: " + by.size());
        Map<String, Area> union = new LinkedHashMap<>();
        for (var e : by.entrySet()) {
            Area u = new Area();
            for (Shape2 s : e.getValue()) u.add(s.area);
            union.put(e.getKey(), u);
            Rectangle2D b = u.getBounds2D();
            double ar = area(u) * 100.0 / 1e6; // unit^2 -> m2 (x100) -> km2
            double sumArea = 0; for (Shape2 s : e.getValue()) sumArea += area(s.area) * 100.0 / 1e6;
            System.out.printf("%s: %d shapes, union %.0f km2 (sum of shapes %.0f), world X %.0f .. %.0f, Z %.0f .. %.0f; pieces in the union: %d%n", e.getKey(), e.getValue().size(), ar, sumArea,
                (b.getMinX() - 2500) * 10, (b.getMaxX() - 2500) * 10, (b.getMinY() - 2500) * 10, (b.getMaxY() - 2500) * 10, pieces(u));
        }
        double all = 0; Area total = new Area(); for (Area u : union.values()) total.add(u);
        System.out.printf("all colours together: %.0f km2 = %.1f%% of the 2500 km2 square%n", area(total) * 100.0 / 1e6, area(total) * 100.0 / 1e6 / 25.0);
        List<String> keys = new ArrayList<>(union.keySet());
        System.out.println("\ncolour pairs: overlap area (km2) / closest approach (m)");
        for (int i = 0; i < keys.size(); i++) for (int j = i + 1; j < keys.size(); j++) {
            Area x = new Area(union.get(keys.get(i))); x.intersect(union.get(keys.get(j)));
            double ov = area(x) * 100.0 / 1e6;
            double gap = ov > 0 ? 0 : gap(union.get(keys.get(i)), union.get(keys.get(j)));
            System.out.printf("%s vs %s: overlap %.1f km2, closest %.0f m%n", keys.get(i), keys.get(j), ov, gap);
        }
        // per-shape: overlaps between shapes of different colours
        System.out.println("\nshapes of different colours that overlap:");
        int n = 0;
        for (int i = 0; i < shapes.size(); i++) for (int j = i + 1; j < shapes.size(); j++) {
            if (shapes.get(i).fill.equals(shapes.get(j).fill)) continue;
            Area x = new Area(shapes.get(i).area); x.intersect(shapes.get(j).area);
            double ov = area(x) * 100.0 / 1e6;
            if (ov > 0.01) { n++; System.out.printf("  shape %d (%s) and %d (%s): %.1f km2%n", i + 1, shapes.get(i).fill, j + 1, shapes.get(j).fill, ov); }
        }
        if (n == 0) System.out.println("  none");
        if (System.getenv("SHAPES_JSON") != null) {
            List<String> colourOrder = new ArrayList<>(union.keySet());
            StringBuilder sb = new StringBuilder("[\n"); double worst = 0; int k = 0;
            for (Shape2 s : shapes) {
                k++;
                double[] f = fit(s.area); // cx, cz (world m), semi-major, semi-minor (m), angle of the major axis from +X towards +Z (degrees, -90..90), area ratio
                worst = Math.max(worst, Math.abs(f[5] - 1));
                sb.append(String.format(java.util.Locale.ROOT, " {\"id\": %d, \"continent\": %d, \"colour\": \"%s\", \"x\": %.0f, \"z\": %.0f, \"major\": %.0f, \"minor\": %.0f, \"angle\": %.1f}%s\n",
                    k, colourOrder.indexOf(s.fill) + 1, s.fill, f[0], f[1], f[2], f[3], f[4], k < shapes.size() ? "," : ""));
            }
            sb.append("]\n");
            Files.writeString(Paths.get(System.getenv("SHAPES_JSON")), sb.toString());
            System.out.printf("wrote %d fitted ellipses; worst area mismatch between path and fitted ellipse %.1f%%%n", shapes.size(), worst * 100);
        }
        if (a.length > 1 && !"reportOnly".equals(a.length > 2 ? a[2] : "")) {
            int W = 2500;
            BufferedImage img = new BufferedImage(W, W, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(222, 190, 128)); g.fillRect(0, 0, W, W);
            g.scale(W / 5000.0, W / 5000.0);
            for (Shape2 s : shapes) { g.setColor(Color.decode(s.fill)); g.fill(s.path); }
            g.setStroke(new BasicStroke(2)); g.setColor(new Color(0, 0, 0, 160));
            for (Shape2 s : shapes) g.draw(s.path);
            g.setColor(Color.BLACK); g.drawLine(2500, 0, 2500, 5000); g.drawLine(0, 2500, 5000, 2500);
            g.setFont(new Font("SansSerif", Font.BOLD, 70)); g.setColor(Color.WHITE);
            int k = 0;
            for (Shape2 s : shapes) { k++; Rectangle2D b = s.area.getBounds2D(); g.drawString(Integer.toString(k), (float) b.getCenterX() - 30, (float) b.getCenterY() + 25); }
            g.dispose();
            ImageIO.write(img, "png", new File(a[1]));
            System.out.println("wrote " + a[1]);
        }
    }

    /** Fit an ellipse to a shape from the second moments of its area (exact for a true ellipse). Returns world metres, north = -Z. */
    static double[] fit(Area ar) {
        Rectangle2D b = ar.getBounds2D(); double step = 1.0, n = 0, sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0;
        for (double y = b.getMinY(); y < b.getMaxY(); y += step) for (double x = b.getMinX(); x < b.getMaxX(); x += step)
            if (ar.contains(x + step / 2, y + step / 2)) { n++; sx += x; sy += y; sxx += x * x; syy += y * y; sxy += x * y; }
        double mx = sx / n, my = sy / n, cxx = sxx / n - mx * mx, cyy = syy / n - my * my, cxy = sxy / n - mx * my;
        double tr = cxx + cyy, det = cxx * cyy - cxy * cxy, disc = Math.sqrt(Math.max(0, tr * tr / 4 - det));
        double l1 = tr / 2 + disc, l2 = tr / 2 - disc; // variances along the major / minor axes: a^2 / 4 and b^2 / 4
        double A = 2 * Math.sqrt(l1), B = 2 * Math.sqrt(Math.max(l2, 0));
        double ang = 0.5 * Math.atan2(2 * cxy, cxx - cyy); // image y grows southwards, as +Z does
        double deg = Math.toDegrees(ang);
        return new double[] {(mx - 2500) * 10, (my - 2500) * 10, A * 10, B * 10, deg, n * step * step / (Math.PI * A * B)};
    }

    static double area(Area ar) { // shoelace over the flattened outline (holes subtract)
        double s = 0; double[] c = new double[6]; double px = 0, py = 0, sx = 0, sy = 0;
        for (PathIterator it = ar.getPathIterator(null, 0.5); !it.isDone(); it.next()) {
            int t = it.currentSegment(c);
            if (t == PathIterator.SEG_MOVETO) { sx = px = c[0]; sy = py = c[1]; }
            else if (t == PathIterator.SEG_LINETO) { s += px * c[1] - c[0] * py; px = c[0]; py = c[1]; }
            else if (t == PathIterator.SEG_CLOSE) { s += px * sy - sx * py; px = sx; py = sy; }
        }
        return Math.abs(s) / 2;
    }

    static int pieces(Area ar) { int n = 0; double[] c = new double[6]; for (PathIterator it = ar.getPathIterator(null, 2); !it.isDone(); it.next()) if (it.currentSegment(c) == PathIterator.SEG_MOVETO) n++; return n; }

    // closest approach of two areas, in metres: boundary sample points of one against the flattened outline of the other
    static double gap(Area p, Area q) {
        List<double[]> pa = outline(p), qa = outline(q); double m = 1e18;
        for (double[] u : pa) for (double[] v : qa) { double d = (u[0] - v[0]) * (u[0] - v[0]) + (u[1] - v[1]) * (u[1] - v[1]); if (d < m) m = d; }
        return Math.sqrt(m) * 10;
    }
    static List<double[]> outline(Area ar) { List<double[]> r = new ArrayList<>(); double[] c = new double[6]; for (PathIterator it = ar.getPathIterator(null, 1.0); !it.isDone(); it.next()) { int t = it.currentSegment(c); if (t == PathIterator.SEG_MOVETO || t == PathIterator.SEG_LINETO) r.add(new double[] {c[0], c[1]}); } return r; }

    static Path2D.Double parse(String d) {
        Path2D.Double p = new Path2D.Double();
        Matcher m = Pattern.compile("([MCLZ])|(-?\\d+\\.?\\d*(?:e-?\\d+)?)").matcher(d);
        List<Double> nums = new ArrayList<>(); char cmd = 0;
        List<Object> toks = new ArrayList<>();
        while (m.find()) toks.add(m.group(1) != null ? (Object) m.group(1).charAt(0) : (Object) Double.parseDouble(m.group(2)));
        int i = 0;
        while (i < toks.size()) {
            Object t = toks.get(i++);
            if (t instanceof Character ch) {
                cmd = ch;
                if (cmd == 'M') p.moveTo((Double) toks.get(i++), (Double) toks.get(i++));
                else if (cmd == 'Z') p.closePath();
            } else { // repeated coordinates
                i--;
                if (cmd == 'C') p.curveTo((Double) toks.get(i++), (Double) toks.get(i++), (Double) toks.get(i++), (Double) toks.get(i++), (Double) toks.get(i++), (Double) toks.get(i++));
                else if (cmd == 'L' || cmd == 'M') p.lineTo((Double) toks.get(i++), (Double) toks.get(i++));
                else throw new IllegalStateException("unsupported path command " + cmd);
            }
        }
        return p;
    }
}
