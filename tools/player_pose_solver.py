"""Player pose solver for Player Animator (KosmX) keyframes - Hominin Evolution.

Pure-stdlib forward kinematics of the vanilla Minecraft 1.21 humanoid model plus the
held-item render chain, with a screen projection for the first-person camera. Use it to
pose an arm-held item (spear, digging stick, thrown rock) and to solve the OTHER arm so
its fist lands on a line (the shaft) that the item traces out of the holding hand.

COORDINATE CONVENTIONS
----------------------
Model space (vanilla, before the renderer's flip). Units are model pixels (16 per block).
    +X = player's LEFT      -X = player's RIGHT
    +Y = DOWN               -Y = UP
    +Z = BACK               -Z = FORWARD
    Origin = top-centre of the torso (shoulder line), 24 px above the feet.
    Right arm pivot (-5, 2, 0); left arm pivot (5, 2, 0). Arm cube spans y -2..10 in its
    own frame: the fist is the last 4 px, centre 8 px from the pivot, fingertips at 10.

"W" space, used for all printed results because it is easier to reason about:
    r = -x (player's right), u = -y (up), f = -z (forward).

ROTATION CONVENTIONS (confirmed from player-animation-lib 2.0.4 bytecode:
AnimationApplier.updatePart copies pitch/yaw/roll straight into ModelPart.xRot/yRot/zRot,
and ModelPart.translateAndRotate uses rotationZYX, i.e. R = Rz(roll) * Ry(yaw) * Rx(pitch),
pitch applied first.)
    pitch 0    = arm hangs straight down
    pitch -90  = arm horizontal, pointing FORWARD      (negative pitch = forward)
    positive yaw on a forward-pointing arm swings the fist toward the player's RIGHT
               (vanilla bow aim: leftArm.yRot = +0.5 puts the left hand across the body)
    roll twists the arm about its own axis (does not move the fist).
Body yaw/pitch/roll rotate ONLY the torso cube - the library does not carry the arms
with it (only a body *bend* propagates to the upper parts). Pose arms directly.

ITEM CHAIN (ItemInHandLayer.renderArmWithItem, right hand):
    arm.translateAndRotate -> mulPose Rx(-90) -> mulPose Ry(180)
    -> translate(1/16, 0.125, -0.625) blocks  (= (1, 2, -10) px, ends at the fingertips)
    -> item display transform: translate(T px), rotationXYZ(rx, ry, rz), scale(S)
    -> translate(-8, -8, -8) so the item model's (8, 8, 8) sits at the hand point.
In that final "F" frame: F+Y = the arm's front face direction, F+Z = up the arm toward
the shoulder, F+X = player's right (arm at rest). So with identity display transform a
hanging arm points the item's +Y forward, and a forward arm points it UP.

FIRST-PERSON SCREEN
    Camera at the eye, W (0, 1.92, 0) (eye height 25.92 px, shoulder line 24 px), looking
    forward. Vertical FOV 70 deg (vanilla default): anything more than 35 deg below the
    horizon is off the bottom of the screen; horizontal half-FOV about 51 deg at 16:9.
"""
import math

# ----------------------------------------------------------------------------- linear algebra
def rx(a):
    a = math.radians(a); c, s = math.cos(a), math.sin(a)
    return [[1, 0, 0], [0, c, -s], [0, s, c]]

def ry(a):
    a = math.radians(a); c, s = math.cos(a), math.sin(a)
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]

def rz(a):
    a = math.radians(a); c, s = math.cos(a), math.sin(a)
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]

def mm(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]

def mv(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]

def transpose(m):
    return [[m[j][i] for j in range(3)] for i in range(3)]

def add(a, b): return [a[i] + b[i] for i in range(3)]
def sub(a, b): return [a[i] - b[i] for i in range(3)]
def scl(a, s): return [a[i] * s for i in range(3)]
def dot(a, b): return sum(a[i] * b[i] for i in range(3))
def norm(a): return math.sqrt(dot(a, a))
def unit(a): return scl(a, 1.0 / norm(a))

# ----------------------------------------------------------------------------- model constants
RIGHT_PIVOT = [-5.0, 2.0, 0.0]
LEFT_PIVOT = [5.0, 2.0, 0.0]
FIST_LOCAL = [0.0, 8.0, 0.0]          # fist centre in the arm's own frame
CAMERA_W = [0.0, 1.92, 0.0]           # first-person eye, W space
FOV_VERTICAL = 70.0
BODY_BOX = ([-4, 0, -2], [4, 12, 2])  # torso cube, model space
HEAD_BOX = ([-4, -8, -4], [4, 0, 4])


def to_w(p):
    """Model space -> W space (r, u, f)."""
    return [-p[0], -p[1], -p[2]]


def arm_rot(pitch, yaw, roll):
    """ModelPart rotation matrix: Rz(roll) * Ry(yaw) * Rx(pitch), degrees."""
    return mm(rz(roll), mm(ry(yaw), rx(pitch)))


def arm_point(pivot, R, local):
    """A point given in the arm's own frame -> model space."""
    return add(pivot, mv(R, local))


def item_point(pivot, R, disp_rot, disp_tr, disp_scale, m):
    """Item-model point m (px, 0..16 cube) -> model space, for the item held in the arm (pivot, R).

    disp_rot   = display "rotation" [rx, ry, rz] degrees (JOML rotationXYZ: Rx*Ry*Rz)
    disp_tr    = display "translation" [x, y, z] in px (the JSON value; vanilla divides by 16)
    disp_scale = display "scale" [sx, sy, sz]
    """
    Rd = mm(rx(disp_rot[0]), mm(ry(disp_rot[1]), rz(disp_rot[2])))
    q = [(m[i] - 8.0) * disp_scale[i] for i in range(3)]
    q = add(mv(Rd, q), disp_tr)                 # F frame, px
    q = add(q, [1.0, 2.0, -10.0])               # translate(1/16, 0.125, -0.625)
    q = mv(mm(rx(-90), ry(180)), q)             # mulPose Rx(-90) then Ry(180)
    return add(pivot, mv(R, q))


def screen_angles(pw):
    """W-space point -> (deg right of crosshair, deg above crosshair, distance px) from the FP camera."""
    d = sub(pw, CAMERA_W)
    r, u, f = d
    return math.degrees(math.atan2(r, f)), math.degrees(math.atan2(u, f)), norm(d)


def on_screen(pw):
    h, v, _ = screen_angles(pw)
    return abs(v) <= FOV_VERTICAL / 2 and abs(h) <= 51


def point_line_dist(p, a, direction):
    """Distance from p to the line through a with unit direction; also the along-line parameter."""
    ap = sub(p, a)
    t = dot(ap, direction)
    return norm(sub(ap, scl(direction, t))), t


def in_box(p, box):
    lo, hi = box
    return all(lo[i] <= p[i] <= hi[i] for i in range(3))


def fist_clear_of_head(fist, margin=2.5):
    """True when a fist centre is far enough outside the head cube not to punch through the face.

    The fist is roughly a 4px cube, so its centre has to clear the head box by a
    couple of pixels. Checked for BOTH fists on every keyframe - a pose that puts a
    hand in the face is never acceptable, however well it grips the shaft."""
    lo, hi = HEAD_BOX
    return any(fist[i] < lo[i] - margin or fist[i] > hi[i] + margin for i in range(3))


def clip_depth(pivot, R, disp_rot, disp_tr, disp_scale, axis_lo=-8, axis_hi=24, samples=33):
    """Worst penetration (px) of the item's long axis (model x=8,z=8, y from axis_lo..axis_hi)
    into the torso or head cube. 0 means clear."""
    worst = 0.0
    for k in range(samples):
        y = axis_lo + (axis_hi - axis_lo) * k / (samples - 1)
        p = item_point(pivot, R, disp_rot, disp_tr, disp_scale, [8, y, 8])
        for box in (BODY_BOX, HEAD_BOX):
            if in_box(p, box):
                lo, hi = box
                depth = min(min(p[i] - lo[i] for i in range(3)), min(hi[i] - p[i] for i in range(3)))
                worst = max(worst, depth)
    return worst


# ----------------------------------------------------------------------------- pose evaluation
def evaluate(right, display, scale, left, axis=((8, -8, 8), (8, 24, 8)), grip=(8, 8, 8)):
    """Full report for a two-arm pose holding an item in the RIGHT hand.

    right   = (pitch, yaw, roll) of the right arm, degrees
    display = (rotation[3], translation[3] px) of thirdperson_righthand
    scale   = display scale (uniform float)
    left    = (pitch, yaw, roll) of the left arm
    axis    = item-model points for the butt and tip of the shaft
    grip    = item-model point that sits in the hand
    Returns a dict; all positions in W space (r, u, f) px.
    """
    disp_rot, disp_tr = display
    S3 = [scale] * 3
    RR = arm_rot(*right)
    RL = arm_rot(*left)
    fist_r = arm_point(RIGHT_PIVOT, RR, FIST_LOCAL)
    fist_l = arm_point(LEFT_PIVOT, RL, FIST_LOCAL)
    g = item_point(RIGHT_PIVOT, RR, disp_rot, disp_tr, S3, list(grip))
    butt = item_point(RIGHT_PIVOT, RR, disp_rot, disp_tr, S3, list(axis[0]))
    tip = item_point(RIGHT_PIVOT, RR, disp_rot, disp_tr, S3, list(axis[1]))
    s = unit(sub(tip, g))
    dL, tL = point_line_dist(fist_l, g, s)
    dR, tR = point_line_dist(fist_r, g, s)
    arm_dir = unit(mv(RR, [0, 1, 0]))
    cam_min = min(norm(sub(to_w(item_point(RIGHT_PIVOT, RR, disp_rot, disp_tr, S3,
                                             [8, axis[0][1] + (axis[1][1] - axis[0][1]) * k / 32, 8])), CAMERA_W))
                  for k in range(33))
    return {
        "fist_r": to_w(fist_r), "fist_l": to_w(fist_l), "grip": to_w(g), "tip": to_w(tip), "butt": to_w(butt),
        "shaft_dir": to_w(s),
        "left_fist_to_shaft_px": dL, "left_fist_along_shaft_px": tL,
        "right_fist_to_shaft_px": dR, "right_fist_along_shaft_px": tR,
        "fist_separation_px": norm(sub(fist_l, fist_r)),
        "shaft_vs_arm_deg": math.degrees(math.acos(max(-1.0, min(1.0, dot(s, arm_dir))))),
        "screen_fist_r": screen_angles(to_w(fist_r)), "screen_fist_l": screen_angles(to_w(fist_l)),
        "screen_tip": screen_angles(to_w(tip)),
        "torso_clip_px": clip_depth(RIGHT_PIVOT, RR, disp_rot, disp_tr, S3, axis[0][1], axis[1][1]),
        "min_dist_to_camera_px": cam_min,
    }


def print_report(label, rep):
    f = lambda v: tuple(round(x, 1) for x in v)
    print(f"[{label}] shaft dir (r,u,f) {f(rep['shaft_dir'])}  tip on screen (right {rep['screen_tip'][0]:.0f} deg, up {rep['screen_tip'][1]:.0f} deg, {rep['screen_tip'][2]:.0f} px)")
    print(f"   right fist {f(rep['fist_r'])} screen {f(rep['screen_fist_r'][:2])} | left fist {f(rep['fist_l'])} screen {f(rep['screen_fist_l'][:2])} | separation {rep['fist_separation_px']:.1f} px")
    print(f"   left fist {rep['left_fist_to_shaft_px']:.2f} px off shaft ({rep['left_fist_along_shaft_px']:.1f} px from grip); right fist {rep['right_fist_to_shaft_px']:.2f} px off shaft")
    print(f"   butt {f(rep['butt'])} | torso clip {rep['torso_clip_px']:.2f} px | nearest shaft point to camera {rep['min_dist_to_camera_px']:.1f} px | shaft-vs-arm {rep['shaft_vs_arm_deg']:.0f} deg")


# ----------------------------------------------------------------------------- solving the other arm
def solve_left_arm(right, display, scale, along_target=-6.0, min_separation=3.3,
                   pitch_pref=-92.0, step=0.5, axis=((8, -8, 8), (8, 24, 8)), grip=(8, 8, 8),
                   pitch_range=(-110.0, -40.0), yaw_range=(-20.0, 70.0), keep_left=True,
                   avoid_head=True, head_margin=2.5, along_tol=None):
    """Find left-arm (pitch, yaw) so the left fist centre lies on the shaft line.

    along_target   = where along the shaft (px from the grip point, negative = toward the butt)
                     the left fist should sit; it is a soft preference.
    min_separation = minimum fist-centre distance (4.0 = cubes just touching; 3.3 = a hair of overlap,
                     which is invisible in first person).
    Also refuses poses where the left fist crosses to the right of the right fist.
    Returns (pitch, yaw, roll=0, off_line_px, along_px) or None.
    """
    disp_rot, disp_tr = display
    S3 = [scale] * 3
    RR = arm_rot(*right)
    fist_r = arm_point(RIGHT_PIVOT, RR, FIST_LOCAL)
    g = item_point(RIGHT_PIVOT, RR, disp_rot, disp_tr, S3, list(grip))
    tip = item_point(RIGHT_PIVOT, RR, disp_rot, disp_tr, S3, list(axis[1]))
    s = unit(sub(tip, g))
    best = None
    # Ranges are parameters because a carry, an overhead swing and a low follow-through
    # put the left arm in completely different quadrants; the defaults suit a forward
    # two-handed carry like the spear.
    n_p = int((pitch_range[1] - pitch_range[0]) / step); n_y = int((yaw_range[1] - yaw_range[0]) / step)
    for i in range(n_p + 1):
        pL = pitch_range[0] + i * step
        for j in range(n_y + 1):
            yL = yaw_range[0] + j * step
            fist_l = arm_point(LEFT_PIVOT, arm_rot(pL, yL, 0), FIST_LOCAL)
            if norm(sub(fist_l, fist_r)) < min_separation:
                continue
            if keep_left and -fist_l[0] > -fist_r[0] - 1.5:   # keep left fist on the left
                continue
            if avoid_head and not fist_clear_of_head(fist_l, head_margin):
                continue
            d, t = point_line_dist(fist_l, g, s)
            # along_tol pins the grip: without it the solver is free to satisfy
            # "fist on the shaft" by sliding the hand ALONG the shaft between
            # keyframes, which renders as the weapon being pulled through a
            # stationary hand instead of carried by it.
            if along_tol is not None and abs(t - along_target) > along_tol:
                continue
            cost = 4 * d + 0.6 * abs(t - along_target) + 0.02 * abs(pL - pitch_pref)
            if best is None or cost < best[0]:
                best = (cost, pL, yL, d, t)
    if best is None:
        return None
    return (best[1], best[2], 0.0, best[3], best[4])


def shift_item_along_axis(display, px):
    """Return a new display (rotation, translation) with the item slid `px` along its own +Y (tip) axis.
    Positive px = more shaft in front of the hand, less behind (hands nearer the butt)."""
    disp_rot, disp_tr = display
    sF = mv(mm(rx(disp_rot[0]), mm(ry(disp_rot[1]), rz(disp_rot[2]))), [0, 1, 0])
    return (list(disp_rot), add(list(disp_tr), scl(sF, px)))


# ----------------------------------------------------------------------------- example
if __name__ == "__main__":
    # The shipped spear_hold pose (assets/hominin_evolution/player_animations/spear_hold.json) and the
    # sharpened_spear.json thirdperson_righthand block.
    DISPLAY = ([-45, 30, -30], [2, 3, -5.5])   # rotation deg, translation px
    SCALE = 0.9
    RIGHT = (-97.5, -30, 20)                    # pitch, yaw, roll

    solved = solve_left_arm(RIGHT, DISPLAY, SCALE, along_target=-7.7, min_separation=3.3)
    print("solved left arm (pitch, yaw, roll):", tuple(round(x, 1) for x in solved[:3]))
    LEFT = (-100, 23.5, 0)                      # the values written into the JSON (rounded from the solve)
    print_report("spear_hold tick 0", evaluate(RIGHT, DISPLAY, SCALE, LEFT))

    # A common pitch change on BOTH arms keeps the left fist exactly on the shaft (the shoulder offset
    # lies along the pitch axis) - that is how the breathing and the thrust coil are built.
    print_report("spear_hold tick 20 (breath)", evaluate((-95.5, -30, 20), DISPLAY, SCALE, (-98, 23.5, 0)))
    print_report("spear_thrust tick 3 (coil)", evaluate((-85.5, -30, 20), DISPLAY, SCALE, (-88, 23.5, 0)))
    print_report("spear_thrust tick 6 (peak)", evaluate((-104, -30, 20), DISPLAY, SCALE, (-108.5, 24, 0)))
