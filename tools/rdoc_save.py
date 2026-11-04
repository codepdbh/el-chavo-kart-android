# Saves textures of a RenderDoc capture at given events (run inside RenderDoc's Python):
#   CK_RDOC_CAPTURE=<rdc> CK_RDOC_OUT=<dir> CK_RDOC_SAVE="eid:resid[:slice],..." qrenderdoc.exe --python tools/rdoc_save.py
# Writes <dir>/e<eid>_r<resid>.png (floating point targets are tonemapped to 0..1 by RenderDoc's PNG export).
import os
import traceback

try:
    import renderdoc as rd
except Exception:
    pass


def run():
    out_dir = os.environ["CK_RDOC_OUT"]
    os.makedirs(out_dir, exist_ok=True)
    cap = rd.OpenCaptureFile()
    cap.OpenFile(os.environ["CK_RDOC_CAPTURE"], "", None)
    _, controller = cap.OpenCapture(rd.ReplayOptions(), None)
    ids = {str(t.resourceId): t.resourceId for t in controller.GetTextures()}
    for item in os.environ["CK_RDOC_SAVE"].split(","):
        parts = item.split(":")
        eid, res = int(parts[0]), "ResourceId::" + parts[1]
        controller.SetFrameEvent(eid, True)
        s = rd.TextureSave()
        s.resourceId = ids[res]
        s.destType = rd.FileType.PNG
        s.alpha = rd.AlphaMapping.Discard
        if len(parts) > 2:
            s.sample.sampleIndex = int(parts[2])
        controller.SaveTexture(s, os.path.join(out_dir, "e%d_r%s.png" % (eid, parts[1])))
    controller.Shutdown()
    cap.Shutdown()


try:
    run()
except Exception:
    open(os.path.join(os.environ.get("CK_RDOC_OUT", "."), "save_error.txt"), "w").write(traceback.format_exc())
os._exit(0)
