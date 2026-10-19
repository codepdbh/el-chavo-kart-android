// El Chavo Kart — Android Evolved
// Vulkan capability probe. Runs in the isolated ":probe" process (GpuProbeActivity), so a driver crash cannot take
// the launcher down. Reports what the device actually exposes; compatibility is decided from these values, never
// from the SoC name (Snapdragon/Dimensity/Exynos are SoC platforms; Adreno/Mali/Xclipse are GPU families).
#include <jni.h>
#include <vulkan/vulkan.h>

#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

namespace {

class Json {
 public:
  void open(char c) { comma(); out_ += c; first_ = true; }
  void close(char c) { out_ += c; first_ = false; }
  void key(const char* k) { comma(); quote(k); out_ += ':'; first_ = true; }
  void str(const char* k, const std::string& v) { key(k); first_ = false; quote(v); }
  void num(const char* k, unsigned long long v) { key(k); first_ = false; out_ += std::to_string(v); }
  void boolean(const char* k, bool v) { key(k); first_ = false; out_ += v ? "true" : "false"; }
  void item(const std::string& v) { comma(); quote(v); }
  const std::string& text() const { return out_; }

 private:
  void comma() {
    if (!first_) out_ += ',';
    first_ = false;
  }
  void quote(const std::string& s) {
    out_ += '"';
    for (char c : s) {
      if (c == '"' || c == '\\') { out_ += '\\'; out_ += c; }
      else if (static_cast<unsigned char>(c) < 0x20) { char b[8]; snprintf(b, sizeof b, "\\u%04x", c); out_ += b; }
      else out_ += c;
    }
    out_ += '"';
  }
  std::string out_;
  bool first_ = true;
};

std::string version(uint32_t v) {
  return std::to_string(VK_API_VERSION_MAJOR(v)) + "." + std::to_string(VK_API_VERSION_MINOR(v)) + "." +
         std::to_string(VK_API_VERSION_PATCH(v));
}

bool has(const std::vector<VkExtensionProperties>& list, const char* name) {
  for (const auto& e : list) if (strcmp(e.extensionName, name) == 0) return true;
  return false;
}

const char* family(uint32_t vendor) {
  switch (vendor) {
    case 0x5143: return "Adreno (Qualcomm)";
    case 0x13B5: return "Mali (Arm)";
    case 0x144D: return "Xclipse (Samsung, AMD RDNA)";
    case 0x1010: return "PowerVR (Imagination)";
    case 0x10DE: return "NVIDIA";
    case 0x1002: return "AMD";
    default: return "unknown";
  }
}

struct NamedFormat { VkFormat format; const char* name; };

std::string probe() {
  Json j;
  j.open('{');
  uint32_t instanceVersion = VK_API_VERSION_1_0;
  auto enumerateVersion =
      reinterpret_cast<PFN_vkEnumerateInstanceVersion>(vkGetInstanceProcAddr(nullptr, "vkEnumerateInstanceVersion"));
  if (enumerateVersion) enumerateVersion(&instanceVersion);
  j.str("instanceVersion", version(instanceVersion));
  if (instanceVersion < VK_API_VERSION_1_1) {
    j.str("probeError", "Vulkan 1.1 loader not available");
    j.close('}');
    return j.text();
  }
  VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
  app.pApplicationName = "El Chavo Kart GPU probe";
  app.apiVersion = VK_API_VERSION_1_1;
  VkInstanceCreateInfo ici{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
  ici.pApplicationInfo = &app;
  VkInstance instance = VK_NULL_HANDLE;
  VkResult r = vkCreateInstance(&ici, nullptr, &instance);
  if (r != VK_SUCCESS) {
    j.str("probeError", "vkCreateInstance failed: " + std::to_string(r));
    j.close('}');
    return j.text();
  }
  // libvulkan.so of API 26 exports only Vulkan 1.0 symbols: resolve 1.1 entry points through the loader.
  auto getFeatures2 = reinterpret_cast<PFN_vkGetPhysicalDeviceFeatures2>(
      vkGetInstanceProcAddr(instance, "vkGetPhysicalDeviceFeatures2"));
  auto getProperties2 = reinterpret_cast<PFN_vkGetPhysicalDeviceProperties2>(
      vkGetInstanceProcAddr(instance, "vkGetPhysicalDeviceProperties2"));
  if (!getFeatures2 || !getProperties2) {
    vkDestroyInstance(instance, nullptr);
    j.str("probeError", "Vulkan 1.1 entry points unavailable");
    j.close('}');
    return j.text();
  }
  uint32_t count = 0;
  vkEnumeratePhysicalDevices(instance, &count, nullptr);
  std::vector<VkPhysicalDevice> gpus(count);
  vkEnumeratePhysicalDevices(instance, &count, gpus.data());
  j.key("devices");
  j.open('[');
  for (VkPhysicalDevice gpu : gpus) {
    VkPhysicalDeviceProperties p{};
    vkGetPhysicalDeviceProperties(gpu, &p);
    uint32_t n = 0;
    vkEnumerateDeviceExtensionProperties(gpu, nullptr, &n, nullptr);
    std::vector<VkExtensionProperties> ext(n);
    vkEnumerateDeviceExtensionProperties(gpu, nullptr, &n, ext.data());

    // Feature chain: core 1.1 + optional extension structs (only chained when the extension exists).
    VkPhysicalDeviceDescriptorIndexingFeaturesEXT indexing{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_INDEXING_FEATURES_EXT};
    VkPhysicalDeviceFeatures2 features{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2};
    const bool hasIndexing = has(ext, VK_EXT_DESCRIPTOR_INDEXING_EXTENSION_NAME) || p.apiVersion >= VK_API_VERSION_1_2;
    if (hasIndexing) features.pNext = &indexing;
    getFeatures2(gpu, &features);
    VkPhysicalDeviceDriverPropertiesKHR driver{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES_KHR};
    VkPhysicalDeviceProperties2 props2{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2};
    const bool hasDriverProps = has(ext, VK_KHR_DRIVER_PROPERTIES_EXTENSION_NAME) || p.apiVersion >= VK_API_VERSION_1_2;
    if (hasDriverProps) props2.pNext = &driver;
    getProperties2(gpu, &props2);

    j.open('{');
    j.str("name", p.deviceName);
    j.str("gpuFamily", family(p.vendorID));
    j.str("apiVersion", version(p.apiVersion));
    j.num("driverVersionRaw", p.driverVersion);
    if (hasDriverProps) {
      j.str("driverName", driver.driverName);
      j.str("driverInfo", driver.driverInfo);
      j.num("driverId", driver.driverID);
    }
    char id[16];
    snprintf(id, sizeof id, "0x%04X", p.vendorID); j.str("vendorId", id);
    snprintf(id, sizeof id, "0x%04X", p.deviceID); j.str("deviceId", id);
    j.num("deviceType", p.deviceType);
    const auto& l = p.limits;
    j.key("limits"); j.open('{');
    j.num("maxBoundDescriptorSets", l.maxBoundDescriptorSets);
    j.num("maxPerStageDescriptorSamplers", l.maxPerStageDescriptorSamplers);
    j.num("maxPerStageDescriptorSampledImages", l.maxPerStageDescriptorSampledImages);
    j.num("maxPerStageDescriptorStorageBuffers", l.maxPerStageDescriptorStorageBuffers);
    j.num("maxPerStageDescriptorUniformBuffers", l.maxPerStageDescriptorUniformBuffers);
    j.num("maxDescriptorSetSampledImages", l.maxDescriptorSetSampledImages);
    j.num("maxUniformBufferRange", l.maxUniformBufferRange);
    j.num("maxStorageBufferRange", l.maxStorageBufferRange);
    j.num("maxPushConstantsSize", l.maxPushConstantsSize);
    j.num("maxImageDimension2D", l.maxImageDimension2D);
    j.num("maxColorAttachments", l.maxColorAttachments);
    j.num("maxVertexInputAttributes", l.maxVertexInputAttributes);
    j.num("maxComputeSharedMemorySize", l.maxComputeSharedMemorySize);
    j.num("framebufferColorSampleCounts", l.framebufferColorSampleCounts);
    j.num("framebufferDepthSampleCounts", l.framebufferDepthSampleCounts);
    j.num("minStorageBufferOffsetAlignment", l.minStorageBufferOffsetAlignment);
    j.num("minUniformBufferOffsetAlignment", l.minUniformBufferOffsetAlignment);
    j.num("nonCoherentAtomSize", l.nonCoherentAtomSize);
    j.close('}');
    const auto& f = features.features;
    j.key("features"); j.open('{');
    j.boolean("shaderInt64", f.shaderInt64);
    j.boolean("shaderInt16", f.shaderInt16);
    j.boolean("shaderFloat64", f.shaderFloat64);
    j.boolean("textureCompressionBC", f.textureCompressionBC);
    j.boolean("textureCompressionETC2", f.textureCompressionETC2);
    j.boolean("textureCompressionASTC_LDR", f.textureCompressionASTC_LDR);
    j.boolean("geometryShader", f.geometryShader);
    j.boolean("tessellationShader", f.tessellationShader);
    j.boolean("fragmentStoresAndAtomics", f.fragmentStoresAndAtomics);
    j.boolean("vertexPipelineStoresAndAtomics", f.vertexPipelineStoresAndAtomics);
    j.boolean("independentBlend", f.independentBlend);
    j.boolean("dualSrcBlend", f.dualSrcBlend);
    j.boolean("depthClamp", f.depthClamp);
    j.boolean("depthBiasClamp", f.depthBiasClamp);
    j.boolean("fillModeNonSolid", f.fillModeNonSolid);
    j.boolean("samplerAnisotropy", f.samplerAnisotropy);
    j.boolean("occlusionQueryPrecise", f.occlusionQueryPrecise);
    j.boolean("fullDrawIndexUint32", f.fullDrawIndexUint32);
    j.boolean("imageCubeArray", f.imageCubeArray);
    j.boolean("shaderClipDistance", f.shaderClipDistance);
    j.boolean("shaderStorageImageWriteWithoutFormat", f.shaderStorageImageWriteWithoutFormat);
    j.close('}');
    j.key("descriptorIndexing"); j.open('{');
    j.boolean("available", hasIndexing);
    if (hasIndexing) {
      j.boolean("runtimeDescriptorArray", indexing.runtimeDescriptorArray);
      j.boolean("descriptorBindingPartiallyBound", indexing.descriptorBindingPartiallyBound);
      j.boolean("descriptorBindingVariableDescriptorCount", indexing.descriptorBindingVariableDescriptorCount);
      j.boolean("descriptorBindingSampledImageUpdateAfterBind", indexing.descriptorBindingSampledImageUpdateAfterBind);
      j.boolean("descriptorBindingStorageBufferUpdateAfterBind", indexing.descriptorBindingStorageBufferUpdateAfterBind);
      j.boolean("shaderSampledImageArrayNonUniformIndexing", indexing.shaderSampledImageArrayNonUniformIndexing);
    }
    j.close('}');

    // Per-format support for the real use (sampled + linear filter / attachment), not a global flag.
    static const NamedFormat kSampled[] = {
        {VK_FORMAT_BC1_RGBA_UNORM_BLOCK, "BC1"}, {VK_FORMAT_BC2_UNORM_BLOCK, "BC2"},
        {VK_FORMAT_BC3_UNORM_BLOCK, "BC3"}, {VK_FORMAT_BC4_UNORM_BLOCK, "BC4"},
        {VK_FORMAT_BC5_UNORM_BLOCK, "BC5"}, {VK_FORMAT_ETC2_R8G8B8A8_UNORM_BLOCK, "ETC2_RGBA8"},
        {VK_FORMAT_ASTC_4x4_UNORM_BLOCK, "ASTC_4x4"}, {VK_FORMAT_R8G8B8A8_UNORM, "RGBA8"},
        {VK_FORMAT_B8G8R8A8_UNORM, "BGRA8"}, {VK_FORMAT_R5G6B5_UNORM_PACK16, "R5G6B5"},
        {VK_FORMAT_A1R5G5B5_UNORM_PACK16, "A1R5G5B5"}, {VK_FORMAT_R4G4B4A4_UNORM_PACK16, "R4G4B4A4"},
        {VK_FORMAT_R16G16B16A16_SFLOAT, "RGBA16F"}, {VK_FORMAT_R32_SFLOAT, "R32F"},
        {VK_FORMAT_A2B10G10R10_UNORM_PACK32, "A2B10G10R10"}, {VK_FORMAT_B10G11R11_UFLOAT_PACK32, "B10G11R11F"},
        {VK_FORMAT_R8_UNORM, "R8"}, {VK_FORMAT_R8G8_UNORM, "RG8"}, {VK_FORMAT_R16G16_UNORM, "RG16"},
    };
    j.key("sampledFormats"); j.open('{');
    for (const auto& nf : kSampled) {
      VkFormatProperties fp{};
      vkGetPhysicalDeviceFormatProperties(gpu, nf.format, &fp);
      const auto o = fp.optimalTilingFeatures;
      std::string s = (o & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT) ?
          ((o & VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT) ? "sampled+linear" : "sampled") : "no";
      if (o & VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BLEND_BIT) s += "+blend";
      else if (o & VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT) s += "+attachment";
      j.str(nf.name, s);
    }
    j.close('}');
    static const NamedFormat kDepth[] = {
        {VK_FORMAT_D16_UNORM, "D16"}, {VK_FORMAT_X8_D24_UNORM_PACK32, "X8D24"}, {VK_FORMAT_D24_UNORM_S8_UINT, "D24S8"},
        {VK_FORMAT_D32_SFLOAT, "D32F"}, {VK_FORMAT_D32_SFLOAT_S8_UINT, "D32FS8"}, {VK_FORMAT_S8_UINT, "S8"},
    };
    j.key("depthFormats"); j.open('{');
    for (const auto& nf : kDepth) {
      VkFormatProperties fp{};
      vkGetPhysicalDeviceFormatProperties(gpu, nf.format, &fp);
      const auto o = fp.optimalTilingFeatures;
      j.str(nf.name, (o & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) ?
          ((o & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT) ? "attachment+sampled" : "attachment") : "no");
    }
    j.close('}');

    VkPhysicalDeviceMemoryProperties mem{};
    vkGetPhysicalDeviceMemoryProperties(gpu, &mem);
    j.key("memoryHeaps"); j.open('[');
    for (uint32_t i = 0; i < mem.memoryHeapCount; ++i) {
      j.open('{'); j.num("size", mem.memoryHeaps[i].size); j.num("flags", mem.memoryHeaps[i].flags); j.close('}');
    }
    j.close(']');
    j.key("memoryTypes"); j.open('[');
    for (uint32_t i = 0; i < mem.memoryTypeCount; ++i) {
      j.open('{'); j.num("heap", mem.memoryTypes[i].heapIndex); j.num("flags", mem.memoryTypes[i].propertyFlags); j.close('}');
    }
    j.close(']');
    vkGetPhysicalDeviceQueueFamilyProperties(gpu, &n, nullptr);
    std::vector<VkQueueFamilyProperties> queues(n);
    vkGetPhysicalDeviceQueueFamilyProperties(gpu, &n, queues.data());
    j.key("queueFamilies"); j.open('[');
    for (const auto& q : queues) {
      j.open('{'); j.num("flags", q.queueFlags); j.num("count", q.queueCount);
      j.num("timestampValidBits", q.timestampValidBits); j.close('}');
    }
    j.close(']');
    j.boolean("swapchainExtension", has(ext, VK_KHR_SWAPCHAIN_EXTENSION_NAME));
    j.key("extensions"); j.open('[');
    for (const auto& e : ext) j.item(e.extensionName);
    j.close(']');

    // Baseline for the diagnostic renderer only. Game renderer requirements are added once measured.
    std::vector<std::string> missing;
    if (p.apiVersion < VK_API_VERSION_1_1) missing.push_back("Vulkan 1.1");
    if (!has(ext, VK_KHR_SWAPCHAIN_EXTENSION_NAME)) missing.push_back(VK_KHR_SWAPCHAIN_EXTENSION_NAME);
    bool graphics = false;
    for (const auto& q : queues) graphics |= (q.queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0;
    if (!graphics) missing.push_back("graphics queue");
    j.boolean("baselineCompatible", missing.empty());
    j.key("missing"); j.open('[');
    for (const auto& m : missing) j.item(m);
    j.close(']');
    j.close('}');
  }
  j.close(']');
  vkDestroyInstance(instance, nullptr);
  j.close('}');
  return j.text();
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL Java_com_codepdbh_chavokart_GpuProbe_nativeProbe(JNIEnv* env, jclass) {
  return env->NewStringUTF(probe().c_str());
}
