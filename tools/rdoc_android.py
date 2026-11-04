# On-device RenderDoc capture/replay for Android renderer bugs (run inside RenderDoc's Python):
#   qrenderdoc.exe --python tools/rdoc_android.py
# Environment:
#   CK_RDOC_SERIAL   adb serial of the phone (the game must be running with the RenderDoc Vulkan layer, see
#                    scripts/android-rdoc.sh, and must be a debuggable build: build-android.ps1 -Debuggable)
#   CK_RDOC_PID      pid of the game process (com.codepdbh.chavokart:game) for "capture"
#   CK_RDOC_OUT      local output folder
#   CK_RDOC_MODE     "capture": trigger one capture of the running game and copy it to CK_RDOC_OUT/capture.rdc
#                    "dump": replay CK_RDOC_OUT/capture.rdc on the phone (Adreno captures cannot replay on a PC GPU),
#                    write actions.json and final.png like tools/rdoc_dump.py, and save CK_RDOC_SAVE="eid:resid,..."
# Writes CK_RDOC_OUT/rdoc_android.log; errors in error.txt.
import json
import os
import time
import traceback

try:
    import renderdoc as rd
except Exception:
    pass

OUT = os.environ.get("CK_RDOC_OUT", ".")
SERIAL = os.environ.get("CK_RDOC_SERIAL", "")
URL = "adb://" + SERIAL
log = None


def note(text):
    log.write(text + "\n")
    log.flush()


def capture():
    target = None
    # Each process with the layer (launcher and game share the package) listens on localabstract:renderdoc_<port>,
    # first port 38920; forward tcp:<port> to each with adb. Both report the package name, so match the pid.
    for ident in range(38920, 38924):
        target = rd.CreateTargetControl("localhost", ident, "chavokart", True)
        if target and target.Connected() and target.GetPID() == int(os.environ["CK_RDOC_PID"]):
            note("connected ident %d: %s pid %d api %s" % (ident, target.GetTarget(), target.GetPID(), target.GetAPI()))
            break
        if target:
            note("skip ident %d: %s" % (ident, target.GetTarget()))
            target.Shutdown()
        target = None
    if not target:
        note("no target")
        return
    capture_id = None
    deadline = time.time() + 120
    triggered = False
    while time.time() < deadline:
        if not triggered and target.GetAPI():
            target.TriggerCapture(1)
            triggered = True
            note("triggered")
        msg = target.ReceiveMessage(None)
        if msg.type == rd.TargetControlMessageType.NewCapture:
            capture_id = msg.newCapture.captureId
            note("new capture %d %s frame %d" % (capture_id, msg.newCapture.path, msg.newCapture.frameNumber))
            target.CopyCapture(capture_id, os.path.join(OUT, "capture.rdc"))
        elif msg.type == rd.TargetControlMessageType.CaptureCopied:
            note("copied %s" % msg.newCapture.path)
            break
        elif msg.type == rd.TargetControlMessageType.Disconnected:
            note("disconnected")
            break
        time.sleep(0.05)
    target.Shutdown()


def dump():
    protocols = rd.GetDeviceProtocolController("adb")
    devices = protocols.GetDevices()
    note("devices: %s" % list(devices))
    url = protocols.GetProtocolName() + "://" + next(d for d in devices if SERIAL in d)
    note("start server: %s" % protocols.StartRemoteServer(url))
    status, remote = rd.CreateRemoteServerConnection(url)
    note("remote: %s" % status)
    remote_path = remote.CopyCaptureToRemote(os.path.join(OUT, "capture.rdc"), None)
    note("remote path: %s" % remote_path)
    status, controller = remote.OpenCapture(rd.RemoteServer.NoPreference, remote_path, rd.ReplayOptions(), None)
    note("open: %s" % status)
    textures = {t.resourceId: t for t in controller.GetTextures()}
    actions = []

    def walk(action_list):
        for a in action_list:
            actions.append({"eid": a.eventId, "name": a.GetName(controller.GetStructuredFile()),
                            "flags": int(a.flags),
                            "outputs": [str(o) for o in a.outputs if o != rd.ResourceId.Null()],
                            "depth": str(a.depthOut) if a.depthOut != rd.ResourceId.Null() else "",
                            "num_indices": a.numIndices, "copy_dst": str(a.copyDestination)})
            if a.children:
                walk(a.children)

    walk(controller.GetRootActions())
    tex_info = {str(rid): {"w": t.width, "h": t.height, "fmt": str(t.format.Name()), "samples": t.msSamp,
                           "mips": t.mips} for rid, t in textures.items()}
    json.dump({"actions": actions, "textures": tex_info}, open(os.path.join(OUT, "actions.json"), "w"), indent=1)
    ids = {str(rid): rid for rid in textures}
    last = [a for a in actions if a["flags"] & int(rd.ActionFlags.Present)]
    if last:
        controller.SetFrameEvent(last[-1]["eid"], True)
        for rid, t in textures.items():
            if str(rid) == last[-1]["copy_dst"]:
                s = rd.TextureSave()
                s.resourceId = rid
                s.destType = rd.FileType.PNG
                controller.SaveTexture(s, os.path.join(OUT, "final.png"))
                note("saved swap %s" % rid)
                break
    for item in filter(None, os.environ.get("CK_RDOC_SAVE", "").split(",")):
        parts = item.split(":")
        controller.SetFrameEvent(int(parts[0]), True)
        s = rd.TextureSave()
        s.resourceId = ids["ResourceId::" + parts[1]]
        s.destType = rd.FileType.PNG
        s.alpha = rd.AlphaMapping.Discard
        controller.SaveTexture(s, os.path.join(OUT, "e%s_r%s.png" % (parts[0], parts[1])))
    note("actions %d textures %d" % (len(actions), len(textures)))
    remote.CloseCapture(controller)
    remote.ShutdownConnection()


try:
    os.makedirs(OUT, exist_ok=True)
    log = open(os.path.join(OUT, "rdoc_android.log"), "a")
    capture() if os.environ.get("CK_RDOC_MODE") == "capture" else dump()
except Exception:
    open(os.path.join(OUT, "error.txt"), "w").write(traceback.format_exc())
os._exit(0)
