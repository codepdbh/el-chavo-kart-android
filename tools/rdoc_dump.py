# RenderDoc capture inspection for El Chavo Kart renderer bugs (run inside RenderDoc's Python):
#   qrenderdoc.exe --python tools/rdoc_dump.py
# Reads CK_RDOC_CAPTURE (capture path) and CK_RDOC_OUT (output folder). Writes actions.json (every action with
# its outputs) and final.png (last presented image), then quits.
import json
import os
import sys
import traceback

try:
    import renderdoc as rd
except Exception:
    pass

def run():
    capture_path = os.environ["CK_RDOC_CAPTURE"]
    out_dir = os.environ["CK_RDOC_OUT"]
    os.makedirs(out_dir, exist_ok=True)
    log = open(os.path.join(out_dir, "dump.log"), "w")

    cap = rd.OpenCaptureFile()
    status = cap.OpenFile(capture_path, "", None)
    log.write("open: %s\n" % status)
    status, controller = cap.OpenCapture(rd.ReplayOptions(), None)
    log.write("replay: %s\n" % status)

    textures = {t.resourceId: t for t in controller.GetTextures()}
    actions = []


    def walk(action_list):
        for a in action_list:
            rec = {"eid": a.eventId, "name": a.GetName(controller.GetStructuredFile()),
                   "flags": int(a.flags), "outputs": [str(o) for o in a.outputs if o != rd.ResourceId.Null()],
                   "depth": str(a.depthOut) if a.depthOut != rd.ResourceId.Null() else "",
                   "num_indices": a.numIndices, "copy_dst": str(a.copyDestination)}
            actions.append(rec)
            if a.children:
                walk(a.children)


    walk(controller.GetRootActions())
    tex_info = {}
    for rid, t in textures.items():
        tex_info[str(rid)] = {"w": t.width, "h": t.height, "fmt": str(t.format.Name()), "samples": t.msSamp,
                              "name": "", "mips": t.mips}
    for r in controller.GetResources():
        if str(r.resourceId) in tex_info:
            tex_info[str(r.resourceId)]["name"] = r.name
    json.dump({"actions": actions, "textures": tex_info}, open(os.path.join(out_dir, "actions.json"), "w"), indent=1)

    # Final presented image: the source of the last present/copy into the swapchain.
    last = None
    for rec in actions:
        if rec["flags"] & int(rd.ActionFlags.Present):
            last = rec
    log.write("present: %s\n" % last)
    if last:
        controller.SetFrameEvent(last["eid"], True)
        for rid, t in textures.items():
            if t.creationFlags & rd.TextureCategory.SwapBuffer:
                s = rd.TextureSave()
                s.resourceId = rid
                s.destType = rd.FileType.PNG
                controller.SaveTexture(s, os.path.join(out_dir, "final.png"))
                log.write("saved swap %s\n" % rid)
                break
    log.write("actions: %d textures: %d\n" % (len(actions), len(textures)))
    log.close()
    controller.Shutdown()
    cap.Shutdown()


try:
    run()
except Exception:
    os.makedirs(os.environ.get("CK_RDOC_OUT", "."), exist_ok=True)
    open(os.path.join(os.environ.get("CK_RDOC_OUT", "."), "error.txt"), "w").write(traceback.format_exc())
os._exit(0)
