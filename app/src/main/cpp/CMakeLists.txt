cmake_minimum_required(VERSION 3.22.1)
project("omniainative")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 11)

set(LLAMA_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)
set(STB_DIR ${LLAMA_CPP_DIR}/vendor/stb)

set(GGML_DIR ${LLAMA_CPP_DIR}/ggml)
set(LLAMA_SRC_DIR ${LLAMA_CPP_DIR}/src)
set(LLAMA_INC_DIR ${LLAMA_CPP_DIR}/include)
set(GGML_INC_DIR ${GGML_DIR}/include)
set(GGML_SRC_DIR ${GGML_DIR}/src)
set(MTMD_DIR ${LLAMA_CPP_DIR}/tools/mtmd)
set(COMMON_DIR ${LLAMA_CPP_DIR}/common)

option(GGML_VULKAN "Enable Vulkan GPU backend" OFF)
option(LLAMA_CURL "Enable curl for model downloads" OFF)
option(GGML_CUDA "Enable CUDA" OFF)
option(GGML_METAL "Enable Metal" OFF)
option(GGML_OPENCL "Enable OpenCL" OFF)
option(GGML_BLAS "Enable BLAS" OFF)

set(GGML_VERSION_MAJOR 0)
set(GGML_VERSION_MINOR 13)
set(GGML_VERSION_PATCH 0)
set(GGML_VERSION "${GGML_VERSION_MAJOR}.${GGML_VERSION_MINOR}.${GGML_VERSION_PATCH}")
set(GGML_BUILD_COMMIT "unknown")
set(GGML_BUILD_NUMBER 0)
set(LLAMA_BUILD_NUMBER 0)
set(LLAMA_BUILD_COMMIT "unknown")
set(BUILD_COMPILER "GNU")
set(BUILD_TARGET "android-arm64")

set(GGML_CPU_SOURCES
    ${GGML_SRC_DIR}/ggml.c
    ${GGML_SRC_DIR}/ggml-alloc.c
    ${GGML_SRC_DIR}/ggml-backend.cpp
    ${GGML_SRC_DIR}/ggml-backend-reg.cpp
    ${GGML_SRC_DIR}/ggml-quants.c
    ${GGML_SRC_DIR}/ggml-opt.cpp
    ${GGML_SRC_DIR}/ggml-threading.cpp
    ${GGML_SRC_DIR}/ggml.cpp
    ${GGML_SRC_DIR}/gguf.cpp
    ${GGML_SRC_DIR}/ggml-backend-dl.cpp
    ${GGML_SRC_DIR}/ggml-backend-meta.cpp
    ${GGML_SRC_DIR}/ggml-cpu/ggml-cpu.cpp
    ${GGML_SRC_DIR}/ggml-cpu/ggml-cpu.c
    ${GGML_SRC_DIR}/ggml-cpu/quants.c
    ${GGML_SRC_DIR}/ggml-cpu/ops.cpp
    ${GGML_SRC_DIR}/ggml-cpu/hbm.cpp
    ${GGML_SRC_DIR}/ggml-cpu/traits.cpp
    ${GGML_SRC_DIR}/ggml-cpu/vec.cpp
    ${GGML_SRC_DIR}/ggml-cpu/repack.cpp
    ${GGML_SRC_DIR}/ggml-cpu/binary-ops.cpp
    ${GGML_SRC_DIR}/ggml-cpu/unary-ops.cpp
)

set(LLAMA_SOURCES
    ${LLAMA_SRC_DIR}/llama.cpp
    ${LLAMA_SRC_DIR}/llama-adapter.cpp
    ${LLAMA_SRC_DIR}/llama-arch.cpp
    ${LLAMA_SRC_DIR}/llama-batch.cpp
    ${LLAMA_SRC_DIR}/llama-chat.cpp
    ${LLAMA_SRC_DIR}/llama-context.cpp
    ${LLAMA_SRC_DIR}/llama-cparams.cpp
    ${LLAMA_SRC_DIR}/llama-grammar.cpp
    ${LLAMA_SRC_DIR}/llama-graph.cpp
    ${LLAMA_SRC_DIR}/llama-hparams.cpp
    ${LLAMA_SRC_DIR}/llama-impl.cpp
    ${LLAMA_SRC_DIR}/llama-io.cpp
    ${LLAMA_SRC_DIR}/llama-kv-cache.cpp
    ${LLAMA_SRC_DIR}/llama-kv-cache-iswa.cpp
    ${LLAMA_SRC_DIR}/llama-memory.cpp
    ${LLAMA_SRC_DIR}/llama-memory-hybrid.cpp
    ${LLAMA_SRC_DIR}/llama-memory-hybrid-iswa.cpp
    ${LLAMA_SRC_DIR}/llama-memory-recurrent.cpp
    ${LLAMA_SRC_DIR}/llama-mmap.cpp
    ${LLAMA_SRC_DIR}/llama-model.cpp
    ${LLAMA_SRC_DIR}/llama-model-loader.cpp
    ${LLAMA_SRC_DIR}/llama-model-saver.cpp
    ${LLAMA_SRC_DIR}/llama-quant.cpp
    ${LLAMA_SRC_DIR}/llama-sampler.cpp
    ${LLAMA_SRC_DIR}/llama-vocab.cpp
    ${LLAMA_SRC_DIR}/unicode.cpp
    ${LLAMA_SRC_DIR}/unicode-data.cpp
)

set(MTMD_SOURCES
    ${MTMD_DIR}/mtmd.cpp
    ${MTMD_DIR}/mtmd-image.cpp
    ${MTMD_DIR}/mtmd-audio.cpp
    ${MTMD_DIR}/mtmd-helper.cpp
    ${MTMD_DIR}/clip.cpp
    ${MTMD_DIR}/deprecation-warning.cpp
)

set(COMMON_SOURCES
    ${COMMON_DIR}/common.cpp
    ${COMMON_DIR}/arg.cpp
    ${COMMON_DIR}/log.cpp
    ${COMMON_DIR}/sampling.cpp
    ${COMMON_DIR}/chat.cpp
    ${COMMON_DIR}/console.cpp
    ${COMMON_DIR}/ngram-cache.cpp
    ${COMMON_DIR}/ngram-map.cpp
    ${COMMON_DIR}/ngram-mod.cpp
    ${COMMON_DIR}/json-partial.cpp
    ${COMMON_DIR}/chat-auto-parser-generator.cpp
    ${COMMON_DIR}/chat-auto-parser-helpers.cpp
    ${COMMON_DIR}/chat-diff-analyzer.cpp
    ${COMMON_DIR}/chat-peg-parser.cpp
    ${COMMON_DIR}/peg-parser.cpp
    ${COMMON_DIR}/regex-partial.cpp
    ${COMMON_DIR}/debug.cpp
    ${COMMON_DIR}/download.cpp
    ${COMMON_DIR}/fit.cpp
    ${COMMON_DIR}/hf-cache.cpp
    ${COMMON_DIR}/json-schema-to-grammar.cpp
    ${COMMON_DIR}/llguidance.cpp
    ${COMMON_DIR}/preset.cpp
    ${COMMON_DIR}/reasoning-budget.cpp
    ${COMMON_DIR}/speculative.cpp
    ${COMMON_DIR}/unicode.cpp
)

configure_file(
    ${COMMON_DIR}/build-info.cpp.in
    ${CMAKE_BINARY_DIR}/build-info.cpp
    @ONLY
)

add_library(omniainative SHARED
    omniainative.cpp
    ${CMAKE_BINARY_DIR}/build-info.cpp
    ${GGML_CPU_SOURCES}
    ${LLAMA_SOURCES}
    ${MTMD_SOURCES}
    ${COMMON_SOURCES}
)

target_include_directories(omniainative PRIVATE
    ${LLAMA_INC_DIR}
    ${GGML_INC_DIR}
    ${GGML_INC_DIR}/ggml-cpu
    ${LLAMA_SRC_DIR}
    ${MTMD_DIR}
    ${COMMON_DIR}
    ${STB_DIR}
    ${LLAMA_CPP_DIR}
    ${GGML_SRC_DIR}
    ${GGML_SRC_DIR}/ggml-cpu
    ${CMAKE_BINARY_DIR}
)

target_compile_definitions(omniainative PRIVATE
    GGML_USE_CPU
    _GNU_SOURCE
    GGML_USE_CPU_AARCH64
    GGML_VERSION="${GGML_VERSION}"
    GGML_COMMIT="${GGML_BUILD_COMMIT}"
    GGML_SHARED
)

if(ANDROID)
    target_compile_definitions(omniainative PRIVATE
        __ANDROID__
    )
endif()

target_compile_options(omniainative PRIVATE
    -Wno-format
    -Wno-unused-variable
    -Wno-unused-function
    -Wno-missing-field-initializers
    -Wno-sign-compare
    -Wno-deprecated-declarations
    -Wno-format-truncation
    -fvisibility=hidden
    -O3
)

target_link_libraries(omniainative
    android
    log
    m
    z
)
